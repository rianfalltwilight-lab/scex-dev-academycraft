$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$target='D:\Minecraft-Validation\runs\academy-combathud33-20261025'
$base='D:\Minecraft-Validation\bases\academy-ssid23-small-20260908'
if($env:COMPUTERNAME -ine 'VICERACH'){throw 'Unexpected host'}
if(Test-Path -LiteralPath $target){throw 'Existing run root must be reviewed before reuse'}
$free=(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB
$volumes=@(Get-Volume -DriveLetter C,D | Select-Object DriveLetter,SizeRemaining)
if($free -lt 6GB){throw "Insufficient free memory: $free"}
if(($volumes|Where-Object DriveLetter -eq 'C').SizeRemaining -lt 20GB){throw 'C drive below 20 GiB'}
if(($volumes|Where-Object DriveLetter -eq 'D').SizeRemaining -lt (20GB+2*1.5GB)){throw 'D drive below 20 GiB plus twice 1.5 GiB budget'}
$desktop=@(Get-Process explorer | Where-Object SessionId -gt 0)
if($desktop.Count -ne 1){throw 'Exactly one interactive desktop required'}
$manifest=(Get-FileHash -LiteralPath "$base\manifest.json").Hash.ToLowerInvariant()
if($manifest -ne '031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'){throw 'Frozen base identity changed'}
$lease=[IO.File]::Open('D:\Minecraft-Validation\control\client-window.lock','OpenOrCreate','ReadWrite','None')
try {
    $baseline=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | ForEach-Object {
        @{pid=$_.ProcessId;created=$_.CreationDate.ToUniversalTime().ToString('o');name=$_.Name;working_set_bytes=[long]$_.WorkingSetSize}
    })
    [IO.Directory]::CreateDirectory($target)|Out-Null
    $receipt=@{status='PASS';host=$env:COMPUTERNAME;root=$target;created_at=(Get-Date).ToString('o');
        free_memory_bytes=$free;volumes=$volumes;expected_additional_gib=1.5;required_target_gib=23;
        original_base=$base;manifest_sha256=$manifest;desktop_session=$desktop[0].SessionId;
        baseline_java=$baseline;lease_probe='acquired and released; each runner acquires exclusive lease separately';
        purpose='AcademyCraft 0.0.33 combat and HUD isolated verification';preserve='original base, shared cache, foreign processes, player and production data'}
    $json=$receipt|ConvertTo-Json -Depth 6
    [IO.File]::WriteAllText("$target\preflight.json",$json,[Text.UTF8Encoding]::new($false))
    Write-Output $json
} finally {$lease.Dispose()}
