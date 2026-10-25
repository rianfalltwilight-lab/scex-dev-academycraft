#requires -Version 7.0
param(
    [string]$RunRoot='D:/SCEX-Work/01-Mods/Other-Ports/AcademyCraft/0.0.30-20261025/runs/gt-r1',
    [int]$ExpectedTests=430,
    [int]$ExpectedFailures=0,
    [switch]$PrepareOnly
)
$ErrorActionPreference='Stop'
$fallback='D:/SCEX-Work/01-Mods/Other-Ports/AcademyCraft/0.0.30-20261025'
$Base="$fallback/base"
$originalBase='D:/Minecraft-Validation/bases/academy-ssid23-small-20260908'
$originalManifestSha='031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'
$java='C:/Users/Win10/Documents/Codex/2026-09-29/gradle-ai-claude-maven-central-neoforge/work/jdk21/jdk-21.0.12.1+1/bin/java.exe'
$Namespaces='academy,academy_charging,academy_audit,academy_followup,academy_projectiles,academy_native_compat'
$run=[IO.Path]::GetFullPath($RunRoot)
if($env:COMPUTERNAME -ine 'MONARCH' -or (Get-Process -Id $PID).SessionId -le 0){throw 'Interactive Monarch session required'}
if(!$run.StartsWith(([IO.Path]::GetFullPath("$fallback/runs")+'\'),[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected run root'}
$lease=[IO.File]::Open("D:/Minecraft-Validation/control/client-window.lock",'OpenOrCreate','ReadWrite','None')
try {
    if(!(Test-Path -LiteralPath $java)){throw 'Validated Java 21 runtime is missing'}
    if((Get-Volume -DriveLetter D).SizeRemaining -lt (20GB + 2*256MB)){throw 'Disk budget: retain 20 GiB plus twice the 256 MiB run estimate'}
    if((Get-FileHash -LiteralPath "$Base/manifest.json").Hash -ine $originalManifestSha){throw 'Original frozen manifest changed'}
    if((Test-Path "$run/process.json") -or (Test-Path "$run/result.json")){throw 'Select a new run; this directory contains execution evidence'}
    [IO.Directory]::CreateDirectory("$run/mods")|Out-Null
    $runArg=$run.Replace('\','/')
    function Convert-LaunchPaths([string]$text){
        $text=$text.Replace($originalBase,$Base).Replace($originalBase.Replace('/','\'),$Base)
        return $text.Replace("$Base/launch/client.classpath","$runArg/server.classpath")
    }
    $recipe = Get-Content -LiteralPath (Join-Path $Base 'launch/recipe.json') -Raw | ConvertFrom-Json
    $manifest = Get-Content -LiteralPath (Join-Path $Base 'manifest.json') -Raw | ConvertFrom-Json
    foreach ($entry in $manifest.files) {
        if ((Get-FileHash -LiteralPath (Join-Path $Base $entry.path)).Hash -ine $entry.sha256) { throw "Frozen runtime changed: $($entry.path)" }
    }
    [IO.File]::WriteAllText("$run/server.classpath",(Convert-LaunchPaths ([IO.File]::ReadAllText("$Base/launch/client.classpath"))))
    $vm = Convert-LaunchPaths ([IO.File]::ReadAllText((Join-Path $Base 'launch/client.vmargs')))
    $vm = $vm -replace '(?m)^-Dscex.runtime.*\r?\n', ''
    $vm=$vm -replace '(?m)^-Xmx[^\r\n]*\r?\n','' -replace '(?m)^-XX:ActiveProcessorCount=[^\r\n]*\r?\n',''
    $vm += "`n-Xmx1536M`n-XX:ActiveProcessorCount=2`n-Dneoforge.gameTestServer=true`n-Dneoforge.enabledGameTestNamespaces=$Namespaces`n"
    if ($Namespaces.Split(',') -contains 'academy_charging') { $vm += "-Dacademy.chargingOutputGate=true`n" }
    [IO.File]::WriteAllText((Join-Path $run 'server.vmargs'), $vm)
    $program = @('cpw.mods.bootstraplauncher.BootstrapLauncher', '--launchTarget', 'forgeserverdev',
        '--gameDir', '.', '--fml.fmlVersion', '4.0.43', '--fml.mcVersion', '1.21.1',
        '--fml.neoForgeVersion', '21.1.248', '--fml.neoFormVersion', '20240808.144430')
    [IO.File]::WriteAllLines((Join-Path $run 'server.programargs'), $program)
    [IO.File]::WriteAllText((Join-Path $run 'eula.txt'), 'eula=true')
    [IO.File]::WriteAllText((Join-Path $run 'server.properties'), "server-ip=127.0.0.1`nserver-port=0`nonline-mode=false`n")
    $index = [Array]::IndexOf(@($recipe.arguments), '-classpath')
    if ($index -lt 0) { throw 'Frozen classpath missing.' }
    $javaArgs = @("@$(Join-Path $run 'server.vmargs')", '-Dfile.encoding=UTF-8',
        "-Dlog4j2.configurationFile=$(([uri](Join-Path $Base 'launch/log4j2.xml')).AbsoluteUri)", '-classpath',
        (Convert-LaunchPaths $recipe.arguments[$index + 1]), 'net.neoforged.devlaunch.Main', "@$(Join-Path $run 'server.programargs')")
    @{java=$java;arguments=$javaArgs;working_directory=$run;source_manifest_sha256=$originalManifestSha;heap_mib=1536;cpu_workers=2;
        namespaces=$Namespaces;expected_tests=$ExpectedTests;prepared_at=(Get-Date).ToString('o');host=$env:COMPUTERNAME}|
        ConvertTo-Json -Depth 5|Set-Content "$run/derived-launch.json"
    if($PrepareOnly){Write-Output "PREPARED $run; Java was not started";return}
    if((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB -lt 6GB){throw 'Requires 6 GiB free RAM'}
    if((Get-ChildItem "$run/mods" -Filter '*.jar').Count -ne 2){throw 'Expected final release and separate audit probe JARs'}
    $start = [Diagnostics.ProcessStartInfo]::new($java)
    $start.WorkingDirectory = $run; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    foreach ($key in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')) { $start.Environment.Remove($key) | Out-Null }
    foreach ($argument in $javaArgs) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    $out = [IO.File]::Create((Join-Path $run 'stdout.log')); $err = [IO.File]::Create((Join-Path $run 'stderr.log'))
    $forced = $false
    $samples = [Collections.Generic.List[object]]::new()
    try {
        if (!$process.Start()) { throw 'Java did not start.' }
        @{pid=$process.Id;started=$process.StartTime.ToString('o');host=$env:COMPUTERNAME;run_root=$run;session=(Get-Process -Id $PID).SessionId} |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'process.json') -Encoding utf8
        $copyOut = $process.StandardOutput.BaseStream.CopyToAsync($out)
        $copyErr = $process.StandardError.BaseStream.CopyToAsync($err)
        $started = Get-Date
        while (!$process.WaitForExit(2000)) {
            $process.Refresh()
            $free = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory * 1KB
            $samples.Add(@{seconds=((Get-Date)-$started).TotalSeconds;free_bytes=$free;rss_bytes=$process.WorkingSet64})
            if (((Get-Date) - $started).TotalSeconds -gt 300 -or
                $free -lt 2.5GB -or $process.WorkingSet64 -gt 3.5GB) {
                $forced = $true; $process.Kill(); $process.WaitForExit(); break
            }
        }
        $copyOut.GetAwaiter().GetResult(); $copyErr.GetAwaiter().GetResult()
        $exitCode = $process.ExitCode
    } finally { $out.Dispose(); $err.Dispose(); $process.Dispose() }
    $samples | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $run 'memory-samples.json') -Encoding utf8
    $latest = Join-Path $run 'logs/latest.log'
    $log = if (Test-Path -LiteralPath $latest) { [IO.File]::ReadAllText($latest) } else { '' }
    $count = if ($log -match '(\d+) GAME TESTS COMPLETE') { [int]$Matches[1] } else { 0 }
    $failures = if ($log -match '(\d+) required tests failed') { [int]$Matches[1] } else { 0 }
    $passed = !$forced -and $exitCode -eq 0 -and $count -eq $ExpectedTests -and $log.Contains("All $ExpectedTests required tests passed")
    $matched = if ($ExpectedFailures -eq 0) { $passed } else {
        !$forced -and $exitCode -eq $ExpectedFailures -and $count -eq $ExpectedTests -and $failures -eq $ExpectedFailures
    }
    @{passed=$passed; expectation_met=$matched; tests=$count; failures=$failures; expected_failures=$ExpectedFailures;
        expected_tests=$ExpectedTests; exit_code=$exitCode; forced=$forced; process_exited=$true;
        namespaces=$Namespaces; host=$env:COMPUTERNAME; basis='Actual NeoForge GameTestServer; release JAR plus separate probe mod'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'result.json') -Encoding utf8
    if (!$matched) { throw "GameTest gate failed: exit=$exitCode tests=$count failures=$failures forced=$forced" }
} finally { $lease.Dispose() }
