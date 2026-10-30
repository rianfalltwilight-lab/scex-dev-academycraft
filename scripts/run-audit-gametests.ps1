param(
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$Base = 'D:/Minecraft-Validation/bases/academy-ssid23-small-20260908',
    [string]$Namespaces = 'academy_audit',
    [int]$ExpectedTests = 15,
    [int]$ExpectedFailures = 0,
    [ValidateSet('VICERACH','MONARCH')][string]$ExpectedHost = 'VICERACH'
)
$ErrorActionPreference = 'Stop'
if ($env:COMPUTERNAME -ine $ExpectedHost) { throw 'Runtime profile host mismatch.' }
$run = [IO.Path]::GetFullPath($RunRoot)
if (!$run.StartsWith('D:\Minecraft-Validation\runs\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Run outside the isolated lab.' }
$lease = $null
$leaseDeadline = (Get-Date).AddMinutes(5)
while ($null -eq $lease) {
    try { $lease = [IO.File]::Open('D:/Minecraft-Validation/control/client-window.lock', 'OpenOrCreate', 'ReadWrite', 'None') }
    catch [IO.IOException] {
        if ((Get-Date) -ge $leaseDeadline) { throw }
        Start-Sleep -Seconds 2
    }
}
try {
    if ((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory * 1KB -lt 6GB) { throw 'Waiting for 6 GiB free RAM.' }
    $recipe = Get-Content -LiteralPath (Join-Path $Base 'launch/recipe.json') -Raw | ConvertFrom-Json
    $manifest = Get-Content -LiteralPath (Join-Path $Base 'manifest.json') -Raw | ConvertFrom-Json
    foreach ($entry in $manifest.files) {
        if ((Get-FileHash -LiteralPath (Join-Path $Base $entry.path)).Hash -ine $entry.sha256) { throw "Frozen runtime changed: $($entry.path)" }
    }
    $vm = [IO.File]::ReadAllText((Join-Path $Base 'launch/client.vmargs'))
    $vm = $vm -replace '(?m)^-Dscex.runtime.*\r?\n', ''
    $vm += "`n-Dneoforge.gameTestServer=true`n-Dneoforge.enabledGameTestNamespaces=$Namespaces`n"
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
        $recipe.arguments[$index + 1], 'net.neoforged.devlaunch.Main', "@$(Join-Path $run 'server.programargs')")
    $start = [Diagnostics.ProcessStartInfo]::new($recipe.java)
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
        @{pid=$process.Id;started=$process.StartTime.ToString('o');host=$env:COMPUTERNAME;run_root=$run} |
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
        expected_tests=$ExpectedTests; exit_code=$exitCode; forced=$forced;
        namespaces=$Namespaces; host=$env:COMPUTERNAME; basis='Actual NeoForge GameTestServer; release JAR plus separate probe mod'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'result.json') -Encoding utf8
    if (!$matched) { throw "GameTest gate failed: exit=$exitCode tests=$count failures=$failures forced=$forced" }
} finally { $lease.Dispose() }
