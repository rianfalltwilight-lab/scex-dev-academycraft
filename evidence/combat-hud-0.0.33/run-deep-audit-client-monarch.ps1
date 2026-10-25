#requires -Version 7.0
param(
    [string]$RunRoot='D:/SCEX-Work/01-Mods/Other-Ports/AcademyCraft/0.0.33-20261025/runs/client-r1',
    [string]$ExtraSkillCases='',
    [switch]$PrepareOnly
)
$ErrorActionPreference='Stop'
$fallback='D:/SCEX-Work/01-Mods/Other-Ports/AcademyCraft/0.0.33-20261025'
$Base='D:/SCEX-Work/01-Mods/Other-Ports/AcademyCraft/0.0.33-20261025/base'
$originalBase='D:/Minecraft-Validation/bases/academy-ssid23-small-20260908'
$java='C:/Users/Win10/Documents/Codex/2026-09-29/gradle-ai-claude-maven-central-neoforge/work/jdk21/jdk-21.0.12.1+1/bin/java.exe'
$originalManifestSha='031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'
$run=[IO.Path]::GetFullPath($RunRoot)
if($env:COMPUTERNAME -ine 'MONARCH' -or (Get-Process -Id $PID).SessionId -le 0){throw 'Interactive Monarch session required'}
if(!$run.StartsWith(([IO.Path]::GetFullPath("$fallback/runs")+'\'),[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected run root'}
$lease=[IO.File]::Open("D:/Minecraft-Validation/control/client-window.lock",'OpenOrCreate','ReadWrite','None')
try {
    if(!(Test-Path -LiteralPath $java)){throw 'Validated Java 21 runtime is missing'}
    if((Get-FileHash -LiteralPath "$Base/manifest.json").Hash -ine $originalManifestSha){throw 'Original frozen manifest changed'}
    $manifest=Get-Content "$Base/manifest.json" -Raw|ConvertFrom-Json
    foreach($entry in $manifest.files){
        if((Get-FileHash -LiteralPath (Join-Path $Base $entry.path)).Hash -ine $entry.sha256){throw "Frozen base changed: $($entry.path)"}
    }
    if((Test-Path "$run/process.json") -or (Test-Path "$run/result.json") -or (Test-Path "$run/stop-request.txt")){
        throw 'This run has execution evidence; select a new isolated RunRoot'
    }
    [IO.Directory]::CreateDirectory("$run/mods")|Out-Null
    $runArg=$run.Replace('\','/')
    function Convert-LaunchPaths([string]$text) {
        $text=$text.Replace($originalBase,$Base).Replace($originalBase.Replace('/','\'),$Base)
        foreach($name in @('client.vmargs','client.programargs','client.classpath')){
            $text=$text.Replace("$Base/launch/$name","$runArg/$name")
        }
        return $text
    }
    foreach($name in @('client.classpath','client.programargs')){
        $derived=Convert-LaunchPaths ([IO.File]::ReadAllText("$Base/launch/$name"))
        [IO.File]::WriteAllText("$run/$name",$derived)
    }
    $vm=Convert-LaunchPaths ([IO.File]::ReadAllText("$Base/launch/client.vmargs"))
    $vm=$vm -replace '(?m)^-Dscex.runtime.*\r?\n','' -replace '(?m)^-Xmx[^\r\n]*\r?\n','' -replace '(?m)^-XX:ActiveProcessorCount=[^\r\n]*\r?\n',''
    $vm+="`n-Xmx1536M`n-XX:ActiveProcessorCount=2`n"
    [IO.File]::WriteAllText("$run/client.vmargs",$vm)
    [IO.File]::WriteAllText("$run/options.txt","onboardAccessibility:false`npauseOnLostFocus:false`nstartedCleanly:true`nskipMultiplayerWarning:true`nmaxFps:30`nrenderDistance:4`nsimulationDistance:5`nfullscreen:false`nguiScale:2`nlang:ja_jp`n")
    $recipe=Get-Content "$Base/launch/recipe.json" -Raw|ConvertFrom-Json
    $arguments=@($recipe.arguments|Where-Object{$_ -notlike '-Dacademy.*'}|ForEach-Object{Convert-LaunchPaths $_})
    $arguments=@($arguments[0],'-Dacademy.hudLayersClient=true',"-Dlog4j2.configurationFile=$(([uri](Join-Path $Base 'launch/log4j2.xml')).AbsoluteUri)")+$arguments[1..($arguments.Length-1)]
    if($ExtraSkillCases){
        $arguments=@($arguments[0],'-Dacademy.deepAuditClient.bootstrapOnly=true','-Dacademy.extraSkillVisualGate=true',
            "-Dacademy.extraSkillVisualGate.cases=$ExtraSkillCases")+$arguments[1..($arguments.Length-1)]
    }
    @{java=$java;arguments=$arguments;working_directory=$run;source_manifest_sha256=$originalManifestSha;heap_mib=1536;cpu_workers=2;
        extra_skill_cases=$ExtraSkillCases;prepared_at=(Get-Date).ToString('o');host=$env:COMPUTERNAME}|ConvertTo-Json -Depth 5|Set-Content "$run/derived-launch.json"
    if($PrepareOnly){Write-Output "PREPARED $run; Java was not started";return}
    if((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB -lt 6GB){throw 'Requires 6 GiB free RAM'}
    if((Get-Volume -DriveLetter D).SizeRemaining -lt 21GB){throw 'Disk budget: retain 20 GiB plus twice the 0.5 GiB run estimate'}
    if((Get-ChildItem "$run/mods" -Filter '*.jar').Count -ne 2){throw 'Expected final release and separate audit probe JARs'}
    $start=[Diagnostics.ProcessStartInfo]::new($java)
    $start.WorkingDirectory=$run;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    foreach($key in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')){$start.Environment.Remove($key)|Out-Null}
    foreach($argument in $arguments){$start.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    $out=[IO.File]::Create("$run/stdout.log");$err=[IO.File]::Create("$run/stderr.log")
    $forced=$false;$closeReason=$null;$samples=[Collections.Generic.List[object]]::new()
    try {
        if(!$process.Start()){throw 'Client did not start'}
        $began=Get-Date
        @{pid=$process.Id;started=$process.StartTime.ToString('o');host=$env:COMPUTERNAME;session=(Get-Process -Id $PID).SessionId}|ConvertTo-Json|Set-Content "$run/process.json"
        $copyOut=$process.StandardOutput.BaseStream.CopyToAsync($out);$copyErr=$process.StandardError.BaseStream.CopyToAsync($err)
        while(!$process.WaitForExit(2000)){
            $process.Refresh();$free=(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB;$seconds=((Get-Date)-$began).TotalSeconds
            $samples.Add(@{seconds=$seconds;free_bytes=$free;rss_bytes=$process.WorkingSet64})
            if($seconds -gt 300 -or $free -lt 2.5GB -or $process.WorkingSet64 -gt 3.5GB){
                $closeReason='resource-or-timeout';[IO.File]::WriteAllText("$run/stop-request.txt",$closeReason)
                if(!$process.WaitForExit(30000)){$forced=$true;$process.Kill();$process.WaitForExit()};break
            }
        }
        $copyOut.GetAwaiter().GetResult();$copyErr.GetAwaiter().GetResult();$code=$process.ExitCode
    }finally{$out.Dispose();$err.Dispose();$process.Dispose()}
    $samples|ConvertTo-Json -Depth 4|Set-Content "$run/memory-samples.json"
    $gateFile=if($ExtraSkillCases){'academy-extra-skill-gate-result.txt'}else{'academy-hud-layers-client.txt'}
    $gate=if(Test-Path "$run/$gateFile"){[IO.File]::ReadAllText("$run/$gateFile")}else{''}
    $log=if(Test-Path "$run/logs/latest.log"){[IO.File]::ReadAllText("$run/logs/latest.log")}else{''}
    $saved=$log.Contains('All dimensions are saved')
    $passed=!$forced -and !$closeReason -and $code -eq 0 -and $gate.StartsWith('status=PASS') -and $saved
    @{passed=$passed;forced=$forced;exit_code=$code;close_reason=$closeReason;process_exited=$true;saved_after_stop=$saved;host=$env:COMPUTERNAME;visual_review='pending'}|ConvertTo-Json|Set-Content "$run/result.json"
    if(!$passed){throw "Client gate failed: exit=$code forced=$forced saved=$saved"}
}finally{$lease.Dispose()}
