#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Plan','Apply')][string]$Mode = 'Plan',
    [ValidatePattern('^client-r[1-9][0-9]*$')][string]$FinalClientRun
)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$project = 'D:\Minecraft-Projects\academycraft-deep-audit-0.0.29-20261025'
$runRoot = 'D:\SCEX-Work\01-Mods\Other-Ports\AcademyCraft\0.0.33-20261025'
$container = 'D:\SCEX-Work\01-Mods\Other-Ports\AcademyCraft'
$sourceEvidence = Join-Path $project 'evidence\combat-hud-0.0.33'
$retained = Join-Path $sourceEvidence 'cleanup-finalize\monarch'
$receiptPath = Join-Path $retained 'completion.json'
$originalBase = 'D:\Minecraft-Validation\bases\academy-ssid23-small-20260908'
$manifestSha = '031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'
$protected = @($project,'D:\Minecraft-Validation\bases','D:\Minecraft-Validation\receipts',
    'D:\Minecraft-Validation\control','D:\Minecraft-Validation\inputs','D:\Minecraft-Validation\cache',
    (Join-Path $env:USERPROFILE '.gradle'))
if ($env:COMPUTERNAME -ine 'MONARCH') { throw 'This wrapper only runs on MONARCH.' }
if ((Full-LabPath $PSScriptRoot) -ine (Join-Path $sourceEvidence 'cleanup-finalize')) { throw 'Run the reviewed wrapper from its project evidence directory.' }
$null = Assert-LabTarget $runRoot $container $protected
Assert-LabOrdinary $retained
function Kept-File([string]$Path) {
    $p = Full-LabPath $Path
    if ($p -ieq $runRoot -or (Is-LabChild $p $runRoot)) { throw 'Retained input/evidence overlaps deletion.' }
    Assert-LabOrdinary $p
    return @{path=$p; sha256=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}
}
function Retain-File([string]$Source, [string]$Destination) {
    Assert-LabOrdinary $Source; Assert-LabOrdinary $Destination
    if (Test-Path -LiteralPath $Destination) {
        if ((Get-FileHash -LiteralPath $Source).Hash -ine (Get-FileHash -LiteralPath $Destination).Hash) { throw "Retained bytes differ: $Destination" }
    } else {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Destination)) | Out-Null
        Copy-Item -LiteralPath $Source -Destination $Destination
    }
    return Kept-File $Destination
}
$preflightFile = Join-Path $sourceEvidence 'monarch-preflight.json'
$preflight = Read-LabJson $preflightFile
if ($preflight.host -ine 'MONARCH' -or $preflight.root -ine $runRoot -or $preflight.status -ne 'PASS' -or
    $preflight.original_base_host -ine 'VICERACH' -or $preflight.original_base -ine $originalBase -or
    $preflight.manifest_sha256 -ine $manifestSha) { throw 'MONARCH preflight does not identify this exact client fallback.' }
$baseline = @($preflight.baseline_java)
$lease = [IO.File]::Open('D:\Minecraft-Validation\control\client-window.lock','OpenOrCreate','ReadWrite','None')
try {
    if (Test-Path -LiteralPath "$runRoot\runs") {
        foreach ($directory in Get-ChildItem -LiteralPath "$runRoot\runs" -Directory) {
            if ($directory.Name -notmatch '^client-r[1-9][0-9]*$') { throw 'Local fallback contains an unreviewed runtime.' }
            $path = Join-Path $directory.FullName 'process.json'
            if (!(Test-Path -LiteralPath $path)) { continue }
            $record = Read-LabJson $path
            if ($record.host -ine 'MONARCH') { throw 'Local process receipt names another host.' }
            $live = Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
            if ($live -and $live.StartTime.ToUniversalTime() -eq ([DateTimeOffset]::Parse($record.started)).UtcDateTime) {
                throw "Recorded client process is still alive: $($record.pid)"
            }
        }
    }
    Assert-LabIdle @($runRoot) $baseline @()
    if ($Mode -eq 'Apply') {
        $receipt = Read-LabJson $receiptPath
        if ($receipt.host -ine 'MONARCH' -or $receipt.run_root -ine $runRoot -or
            $receipt.temporary_container -ine $container -or @($receipt.cleanup_targets).Count -ne 1 -or
            $receipt.cleanup_targets[0].path -ine $runRoot) { throw 'Sealed receipt targets another workflow.' }
        & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath -Apply
        return
    }
    if (!$FinalClientRun) { throw 'Plan requires the explicit successful MONARCH client run.' }
    if (Test-Path -LiteralPath $receiptPath) { throw 'Completion is already sealed; review it and use Apply.' }
    $finalFile = Join-Path $sourceEvidence 'final-validation.json'
    $final = Read-LabJson $finalFile
    if ($final.status -ne 'PASS' -or $final.version -ne '0.0.33-scex-combat-hud' -or $final.visual_review.status -ne 'PASS' -or
        @($final.artifact_entry_comparisons).Count -ne 2) { throw 'Mixed workflow final validation is incomplete.' }
    $evidence = @((Kept-File $preflightFile), (Kept-File $finalFile),
        (Kept-File (Join-Path $sourceEvidence 'client-fallback.json')), (Kept-File (Join-Path $sourceEvidence 'monarch-disk-budget.json')))
    foreach ($name in @('Invoke-Academy33MonarchCleanup.ps1','Lab.Common.ps1','Remove-CompletedTestRun.ps1')) {
        $evidence += Kept-File (Join-Path $PSScriptRoot $name)
    }
    $inputs = @((Retain-File (Join-Path $runRoot 'base\manifest.json') (Join-Path $retained 'base-manifest.json')))
    if ($inputs[0].sha256 -ine $manifestSha) { throw 'Copied base manifest differs from the retained original VICERACH base.' }
    $clientRoot = Join-Path $runRoot "runs\$FinalClientRun"
    $clientAlias = Join-Path $sourceEvidence 'client-final'
    $evidence += Retain-File (Join-Path $clientRoot 'result.json') (Join-Path $clientAlias 'result.json')
    foreach ($name in @('process.json','derived-launch.json')) {
        $evidence += Retain-File (Join-Path $clientRoot $name) (Join-Path $clientAlias $name)
    }
    $exitCodes = @(); $exitSources = @()
    foreach ($selection in @(@{alias='gametests-final'; host='VICERACH'; result=$final.gametests},
            @{alias='client-final'; host='MONARCH'; result=$final.client})) {
        $folder = Join-Path $sourceEvidence $selection.alias
        $resultFile = Join-Path $folder 'result.json'; $result = Read-LabJson $resultFile
        foreach ($entry in @($result,$selection.result)) {
            if ($entry.host -ine $selection.host -or !$entry.passed -or !$entry.process_exited -or $entry.forced -or $entry.exit_code -ne 0) {
                throw 'Final receipts lack normal successful exits on their actual hosts.'
            }
        }
        if ($selection.host -eq 'MONARCH' -and (!$result.saved_after_stop -or $result.close_reason -or !$final.client.saved_after_stop)) {
            throw 'MONARCH client did not save normally.'
        }
        foreach ($name in @('process.json','derived-launch.json','memory-samples.json','artifacts.json','stdout.log','stderr.log','latest.log')) {
            $evidence += Kept-File (Join-Path $folder $name)
        }
        $process = Read-LabJson (Join-Path $folder 'process.json')
        if ($process.host -ine $selection.host) { throw 'Process evidence names another host.' }
        $artifacts = @(Read-LabJson (Join-Path $folder 'artifacts.json'))
        foreach ($comparison in $final.artifact_entry_comparisons) {
            $serverArtifact = $selection.host -eq 'VICERACH'
            $testedPath = if ($serverArtifact) { $comparison.server_tested } else { $comparison.tested }
            $testedSha = if ($serverArtifact) { $comparison.server_tested_sha256 } else { $comparison.tested_sha256 }
            $entriesVerified = if ($serverArtifact) { $comparison.server_common_entries_identical } else { $comparison.runtime_entries_identical }
            $name = [IO.Path]::GetFileName($testedPath)
            $artifact = @($artifacts | Where-Object { $_.name -ceq $name })
            if (!$entriesVerified -or $artifact.Count -ne 1 -or $artifact[0].sha256 -ine $testedSha) {
                throw 'Runtime artifact evidence differs from its host-specific tested snapshot or entry verification.'
            }
        }
        $exitCodes += [int]$result.exit_code
        $exitSources += @{host=$selection.host; path=$resultFile; sha256=(Get-FileHash -LiteralPath $resultFile).Hash}
        foreach ($file in Get-LabInventory $folder) { $evidence += Kept-File $file.FullName }
    }
    foreach ($comparison in $final.artifact_entry_comparisons) {
        foreach ($snapshot in @(@{role='client-tested'; path=$comparison.tested; sha256=$comparison.tested_sha256},
                @{role='server-tested'; path=$comparison.server_tested; sha256=$comparison.server_tested_sha256})) {
            $input = Kept-File $snapshot.path
            if (!(Is-LabChild $input.path (Join-Path $project 'build')) -or $input.sha256 -ine $snapshot.sha256) {
                throw 'Retained tested JAR is missing, outside project build, or differs from its recorded host-specific snapshot.'
            }
            if ($snapshot.role -eq 'client-tested' -and
                (Get-FileHash -LiteralPath (Join-Path $clientRoot (Join-Path 'mods' ([IO.Path]::GetFileName($input.path))))).Hash -ine $input.sha256) {
                throw 'Actual client JAR differs from the retained client-tested snapshot.'
            }
            $input.role = $snapshot.role
            $inputs += $input
        }
    }
    foreach ($name in @('academy-hud-layers-client.txt','academy-hud-layers-save-checkpoint.txt','visual-review.json')) {
        $evidence += Kept-File (Join-Path $clientAlias $name)
    }
    $visual = Read-LabJson (Join-Path $clientAlias 'visual-review.json')
    if ($visual.status -ne 'PASS' -or !@($visual.images).Count) { throw 'Visual review is not complete.' }
    foreach ($image in $visual.images) {
        $path = Full-LabPath (Join-Path $clientAlias $image.file)
        if (!(Is-LabChild $path $clientAlias) -or (Get-FileHash -LiteralPath $path).Hash -ine $image.sha256) { throw 'Reviewed screenshot is missing or changed.' }
    }
    $history = @()
    foreach ($directory in Get-ChildItem -LiteralPath "$runRoot\runs" -Directory) {
        $resultFile = Join-Path $directory.FullName 'result.json'
        $history += @{host='MONARCH'; run=$directory.Name; final=($directory.Name -eq $FinalClientRun);
            result=$(if (Test-Path -LiteralPath $resultFile) { Read-LabJson $resultFile } else { $null })}
        foreach ($file in Get-ChildItem -LiteralPath $directory.FullName -File) {
            if ($file.Extension -notin @('.json','.txt','.log','.vmargs','.programargs','.classpath')) { continue }
            $evidence += Retain-File $file.FullName (Join-Path $retained "attempts\$($directory.Name)\$($file.Name)")
        }
        foreach ($sub in @('logs','screenshots','crash-reports')) {
            $folder = Join-Path $directory.FullName $sub
            if (!(Test-Path -LiteralPath $folder)) { continue }
            foreach ($file in Get-ChildItem -LiteralPath $folder -File) {
                if ($sub -eq 'logs' -and $file.Name -notin @('latest.log','debug.log')) { continue }
                $evidence += Retain-File $file.FullName (Join-Path $retained "attempts\$($directory.Name)\$sub\$($file.Name)")
            }
        }
    }
    $identity = Get-Item -LiteralPath $runRoot -Force
    Write-LabJson $receiptPath @{schema_version=1; host='MONARCH'; run_id='academy-combathud33-20261025-client';
        run_root=$runRoot; temporary_container=$container; complete=$true; status='COMPLETE'; pending_checks=@();
        exit_codes=$exitCodes; exit_sources=$exitSources; final_client_run=$FinalClientRun; attempt_history=$history;
        inputs=$inputs; evidence=@($evidence | Sort-Object path -Unique); java_baseline=$baseline; ports=@(); protected_roots=$protected;
        cleanup_targets=@(@{kind='disposable-test-copy'; path=$runRoot; created_utc=$identity.CreationTimeUtc.ToString('o')});
        base_origin=@{host='VICERACH'; path=$originalBase; manifest_sha256=$manifestSha; preserved=$true};
        final_inputs=$final.artifact_entry_comparisons; final_artifacts=$final.artifacts;
        basis='Actual MONARCH client completed with normal zero exit, native save and image review; external VICERACH final GameTest passed. Server evidence matches server_tested_sha256 with server_common_entries_identical verified; client evidence matches tested_sha256. Both server/client main and probe snapshots remain hashed inputs outside deletion; whole server/client JAR equality is not claimed. Earlier local attempt results remain unchanged. Copied base is disposable; its manifest is retained here and original VICERACH base remains intact. Source, both tested snapshots, deliverables and evidence remain outside the precise fallback root.'}
    & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath
} finally { $lease.Dispose() }
