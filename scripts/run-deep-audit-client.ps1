#requires -Version 7.0
param([Parameter(Mandatory)][string]$RunRoot,
      [string]$Base='D:/Minecraft-Validation/bases/academy-ssid23-small-20260908',
      [string]$ExtraSkillCases='')
$ErrorActionPreference='Stop'
$run=[IO.Path]::GetFullPath($RunRoot)
if($env:COMPUTERNAME -ine 'VICERACH' -or (Get-Process -Id $PID).SessionId -le 0){throw 'Interactive Vicerach session required'}
if(!$run.StartsWith('D:\Minecraft-Validation\runs\academy-deepaudit29-', [StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected run root'}
$lease=[IO.File]::Open('D:/Minecraft-Validation/control/client-window.lock','OpenOrCreate','ReadWrite','None')
try {
    if((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB -lt 6GB){throw 'Requires 6 GiB free RAM'}
    if((Get-Volume -DriveLetter D).SizeRemaining -lt 21GB){throw 'Disk budget: retain 20 GiB plus twice the 0.5 GiB estimate'}
    $recipe=Get-Content "$Base/launch/recipe.json" -Raw|ConvertFrom-Json
    $manifest=Get-Content "$Base/manifest.json" -Raw|ConvertFrom-Json
    foreach($entry in $manifest.files){if((Get-FileHash -LiteralPath (Join-Path $Base $entry.path)).Hash -ine $entry.sha256){throw "Frozen base changed: $($entry.path)"}}
    if((Get-ChildItem "$run/mods" -Filter '*.jar').Count -ne 2){throw 'Expected release and separate audit probe JARs'}
    $vm=[IO.File]::ReadAllText("$Base/launch/client.vmargs") -replace '(?m)^-Dscex.runtime.*\r?\n',''
    [IO.File]::WriteAllText("$run/client.vmargs",$vm)
    [IO.File]::WriteAllText("$run/options.txt","onboardAccessibility:false`nstartedCleanly:true`nskipMultiplayerWarning:true`nmaxFps:30`nrenderDistance:4`nsimulationDistance:5`nfullscreen:false`nguiScale:2`nlang:ja_jp`n")
    $arguments=@($recipe.arguments | Where-Object{$_ -notlike '-Dacademy.*'} | ForEach-Object{if($_ -like '@*/client.vmargs'){"@$run/client.vmargs"}else{$_}})
    $arguments=@($arguments[0],'-Dacademy.deepAuditClient=true',"-Dlog4j2.configurationFile=$(([uri](Join-Path $Base 'launch/log4j2.xml')).AbsoluteUri)")+$arguments[1..($arguments.Length-1)]
    if ($ExtraSkillCases) {
        $arguments=@($arguments[0],'-Dacademy.deepAuditClient.bootstrapOnly=true','-Dacademy.extraSkillVisualGate=true',
            "-Dacademy.extraSkillVisualGate.cases=$ExtraSkillCases")+$arguments[1..($arguments.Length-1)]
    }
    $start=[Diagnostics.ProcessStartInfo]::new($recipe.java)
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
    $gateFile=if($ExtraSkillCases){'academy-extra-skill-gate-result.txt'}else{'academy-deep-audit-client.txt'}
    $gate=if(Test-Path "$run/$gateFile"){[IO.File]::ReadAllText("$run/$gateFile")}else{''}
    $log=if(Test-Path "$run/logs/latest.log"){[IO.File]::ReadAllText("$run/logs/latest.log")}else{''}
    $saved=$log.Contains('All dimensions are saved')
    $passed=!$forced -and !$closeReason -and $code -eq 0 -and $gate.StartsWith('status=PASS') -and $saved
    @{passed=$passed;forced=$forced;exit_code=$code;close_reason=$closeReason;process_exited=$true;saved_after_stop=$saved;host=$env:COMPUTERNAME;visual_review='pending'}|ConvertTo-Json|Set-Content "$run/result.json"
    if(!$passed){throw "Client gate failed: exit=$code forced=$forced saved=$saved"}
}finally{$lease.Dispose()}
