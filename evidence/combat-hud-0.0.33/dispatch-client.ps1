#requires -Version 7.0
param([Parameter(Mandatory)][ValidatePattern('^client-r[0-9]+$')][string]$RunId)
$ErrorActionPreference='Stop'
$root='D:\Minecraft-Validation\runs\academy-combathud33-20261025'
$run=Join-Path $root "runs\$RunId"
$pwsh='C:\Users\4090\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
if($env:COMPUTERNAME -ine 'VICERACH'){throw 'Unexpected host'}
$desktop=@(Get-Process explorer | Where-Object SessionId -gt 0)
if($desktop.Count -ne 1){throw 'One logged-in desktop required'}
if((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB -lt 6GB){throw 'Requires 6 GiB free memory'}
if((Test-Path "$run/process.json") -or (Test-Path "$run/result.json")){throw 'Run already executed'}
$taskName="Codex-AcademyCombatHud33-$RunId"
if(Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue){throw 'Task already exists'}
$runner=Join-Path $root 'run-deep-audit-client-vicerach.ps1'
$arguments='-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "{0}" -RunRoot "{1}"' -f $runner,$run
$action=New-ScheduledTaskAction -Execute $pwsh -Argument $arguments -WorkingDirectory $root
$principal=New-ScheduledTaskPrincipal -UserId "$env:COMPUTERNAME\4090" -LogonType Interactive -RunLevel Limited
$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::FromMinutes(10)) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Settings $settings | Out-Null
@{task_name=$taskName;run_id=$RunId;run_root=$run;runner_sha256=(Get-FileHash $runner).Hash;desktop_session=$desktop[0].SessionId;created_at=(Get-Date).ToString('o');autostart=$false}|ConvertTo-Json|Set-Content "$run/dispatch.json"
Start-ScheduledTask -TaskName $taskName
Get-Content "$run/dispatch.json" -Raw
