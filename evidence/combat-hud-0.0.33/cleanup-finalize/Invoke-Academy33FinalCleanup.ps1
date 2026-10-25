#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Plan','Apply')][string]$Mode = 'Plan',
    [ValidatePattern('^gt-r[1-9][0-9]*$')][string]$FinalGameTestRun,
    [ValidatePattern('^client-r[1-9][0-9]*$')][string]$FinalClientRun
)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$runRoot = 'D:\Minecraft-Validation\runs\academy-combathud33-20261025'
$container = 'D:\Minecraft-Validation\runs'
$retained = 'D:\Minecraft-Validation\receipts\academy-combathud33-20261025'
$base = 'D:\Minecraft-Validation\bases\academy-ssid23-small-20260908'
$manifestSha = '031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'
$receiptPath = Join-Path $retained 'completion.json'
$monarchRoot = 'D:\SCEX-Work\01-Mods\Other-Ports\AcademyCraft\0.0.33-20261025'
$protected = @('D:\Minecraft-Projects','D:\Minecraft-Validation\bases','D:\Minecraft-Validation\receipts',
    'D:\Minecraft-Validation\control','D:\Minecraft-Validation\inputs','D:\Minecraft-Validation\cache',
    (Join-Path $env:USERPROFILE '.gradle'))
if ($env:COMPUTERNAME -ine 'VICERACH') { throw 'This wrapper only runs on VICERACH.' }
if ((Full-LabPath $PSScriptRoot) -ine $retained) { throw 'Place this wrapper and both original helpers in the fixed receipts directory.' }
$null = Assert-LabTarget $runRoot $container $protected
Assert-LabOrdinary $retained

function Kept-File([string]$Path) {
    $p = Full-LabPath $Path
    if ($p -ieq $runRoot -or (Is-LabChild $p $runRoot)) { throw 'Retained evidence overlaps deletion.' }
    Assert-LabOrdinary $p
    return @{path=$p; sha256=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}
}
function Retain-File([string]$Source, [string]$Destination) {
    Assert-LabOrdinary $Source; Assert-LabOrdinary $Destination
    if (Test-Path -LiteralPath $Destination) {
        if ((Get-FileHash -LiteralPath $Source).Hash -ine (Get-FileHash -LiteralPath $Destination).Hash) {
            throw "Retained evidence differs: $Destination"
        }
    } else {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Destination)) | Out-Null
        Copy-Item -LiteralPath $Source -Destination $Destination
    }
    return Kept-File $Destination
}
function Assert-RecordedProcessesExited {
    if (!(Test-Path -LiteralPath "$runRoot\runs")) { return }
    foreach ($directory in Get-ChildItem -LiteralPath "$runRoot\runs" -Directory) {
        $path = Join-Path $directory.FullName 'process.json'
        if (!(Test-Path -LiteralPath $path)) { continue }
        $record = Read-LabJson $path
        if ($record.host -ine 'VICERACH') { throw 'Runtime process receipt names another host.' }
        $live = Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
        if ($live -and $live.StartTime.ToUniversalTime() -eq ([DateTimeOffset]::Parse($record.started)).UtcDateTime) {
            throw "Recorded test process is still alive: $($record.pid)"
        }
    }
}
function Assert-UnstartedClients {
    if (!(Test-Path -LiteralPath $runRoot)) { return }
    foreach ($id in @('client-r1','client-r2','client-r3')) {
        $directory = Join-Path $runRoot "runs\$id"
        if (!(Test-Path -LiteralPath $directory -PathType Container)) { throw "Expected client staging is missing: $id" }
        foreach ($name in @('process.json','result.json','dispatch.json')) {
            if (Test-Path -LiteralPath (Join-Path $directory $name)) { throw "Remote client was not merely staged: $id/$name" }
        }
        if (Get-ScheduledTask -TaskName "Codex-AcademyCombatHud33-$id" -TaskPath '\' -ErrorAction SilentlyContinue) {
            throw "Unexpected remote client task exists without an authorized dispatch: $id"
        }
    }
    foreach ($directory in Get-ChildItem -LiteralPath "$runRoot\runs" -Directory -Filter 'client-*') {
        if ($directory.Name -notin @('client-r1','client-r2','client-r3')) { throw 'Unreviewed remote client staging exists.' }
    }
}

$preflightFile = Join-Path $retained 'preflight.json'
$preflight = Read-LabJson $preflightFile
if ($preflight.host -ine 'VICERACH' -or $preflight.root -ine $runRoot -or $preflight.status -ne 'PASS' -or
    $preflight.original_base -ine $base -or $preflight.manifest_sha256 -ine $manifestSha) {
    throw 'Preflight is not the original successful receipt for this host/root/base.'
}
# Use the actual preflight baseline, never a fresh list that could admit a surviving test JVM.
$baseline = @($preflight.baseline_java)
$lease = [IO.File]::Open('D:\Minecraft-Validation\control\client-window.lock','OpenOrCreate','ReadWrite','None')
try {
    Assert-RecordedProcessesExited
    Assert-UnstartedClients
    Assert-LabIdle @($runRoot) $baseline @()
    if ($Mode -ne 'Plan') {
        $receipt = Read-LabJson $receiptPath
        if ($receipt.host -ine 'VICERACH' -or $receipt.run_root -ine $runRoot -or
            $receipt.temporary_container -ine $container -or @($receipt.cleanup_targets).Count -ne 1 -or
            $receipt.cleanup_targets[0].path -ine $runRoot -or !$receipt.complete -or
            $receipt.status -ne 'COMPLETE' -or @($receipt.pending_checks).Count -or
            @($receipt.exit_codes).Count -ne 2 -or @($receipt.exit_codes | Where-Object { $_ -ne 0 }).Count) {
            throw 'Sealed completion receipt differs from this completed workflow.'
        }
        Assert-LabHashes @($receipt.inputs); Assert-LabHashes @($receipt.evidence)
        & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath -Apply
        return
    }
    if (!$FinalGameTestRun -or !$FinalClientRun) { throw 'Plan requires explicit final GameTest and client run names.' }
    if (Test-Path -LiteralPath $receiptPath) { throw 'Completion is already sealed; review it and use Apply. Do not rewrite its hashes.' }
    $finalFile = Join-Path $retained 'final-validation.json'
    $final = Read-LabJson $finalFile
    if ($final.status -ne 'PASS' -or $final.version -ne '0.0.33-scex-combat-hud' -or
        $final.visual_review.status -ne 'PASS') { throw 'Final combat/HUD validation and visual review must pass.' }
    foreach ($entry in @(@{result=$final.gametests; host='VICERACH'}, @{result=$final.client; host='MONARCH'})) {
        $result = $entry.result
        if ($result.host -ine $entry.host -or !$result.passed -or !$result.process_exited -or $result.forced -or $result.exit_code -ne 0) {
            throw 'Final validation does not record actual normal VICERACH GameTest and MONARCH client exits.'
        }
    }
    if (!$final.client.saved_after_stop -or @($final.artifact_entry_comparisons).Count -ne 2) {
        throw 'Final validation lacks the native save or both tested artifact identities.'
    }
    $evidence = @((Kept-File $preflightFile), (Kept-File $finalFile),
        (Kept-File (Join-Path $retained 'monarch-preflight.json')), (Kept-File (Join-Path $retained 'client-fallback.json')))
    foreach ($name in @('Invoke-Academy33FinalCleanup.ps1','Lab.Common.ps1','Remove-CompletedTestRun.ps1')) {
        $evidence += Kept-File (Join-Path $PSScriptRoot $name)
    }
    $inputs = @((Kept-File (Join-Path $base 'manifest.json')))
    if ($inputs[0].sha256 -ine $manifestSha) { throw 'Original frozen base manifest changed.' }
    $exitCodes = @(); $exitSources = @()
    foreach ($selection in @(@{run=$FinalGameTestRun; alias='gametests-final'; host='VICERACH'}, @{run=$FinalClientRun; alias='client-final'; host='MONARCH'})) {
        $actual = Join-Path $runRoot "runs\$($selection.run)"
        $alias = Join-Path $retained $selection.alias
        $resultFile = if ($selection.host -eq 'VICERACH') { Join-Path $actual 'result.json' } else { Join-Path $alias 'result.json' }
        $result = Read-LabJson $resultFile
        if ($result.host -ine $selection.host -or !$result.passed -or !$result.process_exited -or $result.forced -or $result.exit_code -ne 0) {
            throw "Final run lacks actual successful normal exit on its recorded host: $($selection.run)"
        }
        if ($selection.alias -eq 'client-final' -and (!$result.saved_after_stop -or $result.close_reason)) { throw 'Client did not save normally.' }
        $evidence += Retain-File $resultFile (Join-Path $alias 'result.json')
        if ($selection.host -eq 'VICERACH') {
            foreach ($name in @('process.json','derived-launch.json')) {
                $evidence += Retain-File (Join-Path $actual $name) (Join-Path $alias $name)
            }
        }
        foreach ($name in @('process.json','derived-launch.json','memory-samples.json','artifacts.json','stdout.log','stderr.log','latest.log')) {
            $evidence += Kept-File (Join-Path $alias $name)
        }
        if ($selection.alias -eq 'client-final') {
            foreach ($name in @('academy-hud-layers-client.txt','academy-hud-layers-save-checkpoint.txt','visual-review.json')) {
                $evidence += Kept-File (Join-Path $alias $name)
            }
            $visual = Read-LabJson (Join-Path $alias 'visual-review.json')
            if ($visual.status -ne 'PASS' -or !@($visual.images).Count) { throw 'Client image review is not complete.' }
            foreach ($image in $visual.images) {
                $path = Full-LabPath (Join-Path $alias $image.file)
                if (!(Is-LabChild $path $alias) -or (Get-FileHash -LiteralPath $path).Hash -ine $image.sha256) {
                    throw 'Reviewed screenshot is missing, outside the client evidence or changed.'
                }
            }
        }
        $process = Read-LabJson (Join-Path $alias 'process.json')
        if ($process.host -ine $selection.host) { throw 'Final process evidence host differs from its result.' }
        if ($selection.host -eq 'MONARCH') {
            $launch = Read-LabJson (Join-Path $alias 'derived-launch.json')
            if ($launch.host -ine 'MONARCH' -or $launch.working_directory -ine (Join-Path $monarchRoot "runs\$FinalClientRun")) {
                throw 'External client evidence identifies another local run.'
            }
        }
        $artifactRecords = @(Read-LabJson (Join-Path $alias 'artifacts.json'))
        foreach ($comparison in $final.artifact_entry_comparisons) {
            $serverArtifact = $selection.host -eq 'VICERACH'
            $testedPath = if ($serverArtifact) { $comparison.server_tested } else { $comparison.tested }
            $testedSha = if ($serverArtifact) { $comparison.server_tested_sha256 } else { $comparison.tested_sha256 }
            $entriesVerified = if ($serverArtifact) { $comparison.server_common_entries_identical } else { $comparison.runtime_entries_identical }
            if (!$entriesVerified) { throw "Final validation did not verify this host's tested entries: $($selection.host)" }
            $jarName = [IO.Path]::GetFileName($testedPath)
            $record = @($artifactRecords | Where-Object { $_.name -ceq $jarName })
            if ($record.Count -ne 1 -or $record[0].sha256 -ine $testedSha) { throw 'Final artifact receipt differs from its host-specific tested identity.' }
            if ($serverArtifact -and (Get-FileHash -LiteralPath (Join-Path $actual "mods\$jarName")).Hash -ine $testedSha) {
                throw "Actual tested JAR differs from final validation: $($selection.run)/$jarName"
            }
        }
        $exitCodes += [int]$result.exit_code
        $exitSources += @{host=$selection.host; run=$selection.run; path=(Join-Path $alias 'result.json'); sha256=(Get-FileHash -LiteralPath $resultFile).Hash}
        foreach ($file in Get-LabInventory $alias) { $evidence += Kept-File $file.FullName }
    }
    $history = @(); $staging = @()
    $smallNames = @('result.json','process.json','dispatch.json','derived-launch.json','memory-samples.json','artifacts.json',
        'stdout.log','stderr.log','stop-request.txt','academy-hud-layers-client.txt','academy-hud-layers-save-checkpoint.txt',
        'server.vmargs','server.programargs','server.classpath','client.vmargs','client.programargs','client.classpath','options.txt')
    foreach ($directory in Get-ChildItem -LiteralPath "$runRoot\runs" -Directory) {
        if ($directory.Name -notmatch '^(gt|client)-r[1-9][0-9]*$') { throw 'Unexpected directory in registered runtime history.' }
        $resultFile = Join-Path $directory.FullName 'result.json'
        $history += @{host='VICERACH'; run=$directory.Name; final=($directory.Name -eq $FinalGameTestRun);
            status=$(if ($directory.Name -like 'client-*') { 'NOT_STARTED_RESOURCE_GUARD' } else { 'ACTUAL_RUN' });
            result= $(if (Test-Path -LiteralPath $resultFile) { Read-LabJson $resultFile } else { $null })}
        foreach ($name in $smallNames) {
            $source = Join-Path $directory.FullName $name
            if (Test-Path -LiteralPath $source) { $evidence += Retain-File $source (Join-Path $retained "attempts\$($directory.Name)\$name") }
        }
        foreach ($sub in @('logs','screenshots','crash-reports')) {
            $folder = Join-Path $directory.FullName $sub
            if (!(Test-Path -LiteralPath $folder)) { continue }
            foreach ($file in Get-ChildItem -LiteralPath $folder -File) {
                if ($sub -eq 'logs' -and $file.Name -notin @('latest.log','debug.log')) { continue }
                $evidence += Retain-File $file.FullName (Join-Path $retained "attempts\$($directory.Name)\$sub\$($file.Name)")
            }
        }
        if ($directory.Name -like 'client-*') {
            $files = @(foreach ($file in Get-LabInventory $directory.FullName) {
                @{relative_path=[IO.Path]::GetRelativePath($directory.FullName,$file.FullName); bytes=$file.Length;
                    sha256=(Get-FileHash -LiteralPath $file.FullName).Hash}
            })
            $staging += @{host='VICERACH'; run=$directory.Name; status='NOT_STARTED_RESOURCE_GUARD';
                test_java_started=$false; runtime_exit_code=$null; process_result_dispatch_absent=$true; files=$files}
        }
    }
    $stagingFile = Join-Path $retained 'unstarted-client-staging.json'
    Write-LabJson $stagingFile $staging
    $evidence += Kept-File $stagingFile
    $history += @{host='MONARCH'; run=$FinalClientRun; final=$true; status='EXTERNAL_FINAL_ACCEPTANCE';
        result=(Read-LabJson (Join-Path $retained 'client-final\result.json'))}
    $identity = Get-Item -LiteralPath $runRoot -Force
    $receipt = @{schema_version=1; host='VICERACH'; run_id='academy-combathud33-20261025';
        run_root=$runRoot; temporary_container=$container; complete=$true; status='COMPLETE'; pending_checks=@();
        exit_codes=$exitCodes; exit_sources=$exitSources; final_runs=@($FinalGameTestRun,$FinalClientRun);
        attempt_history=$history; inputs=$inputs; evidence=@($evidence | Sort-Object path -Unique);
        java_baseline=$baseline; ports=@(); protected_roots=$protected; remote_client_staging=$staging;
        cleanup_targets=@(@{kind='disposable-test-copy'; path=$runRoot; created_utc=$identity.CreationTimeUtc.ToString('o')});
        base_origin=@{host='VICERACH'; path=$base; manifest_sha256=$manifestSha; preserved=$true};
        final_inputs_host='MONARCH'; final_inputs=$final.artifact_entry_comparisons; final_artifacts=$final.artifacts;
        basis='Actual VICERACH GameTest and external MONARCH client completed with normal zero exits; MONARCH client save and visual review passed. Server receipts/JARs match server_tested_sha256; client receipts match tested_sha256. Final validation verifies unchanged server-common entries and records the exact client-only differences; entire server/client JAR equality is not claimed. All VICERACH client-r1/r2/r3 directories are NOT_STARTED_RESOURCE_GUARD staging with no process/result/dispatch, no runtime exit is invented for them. Original failed GameTest attempts are retained unchanged. Original base, shared dependencies, MONARCH source/both tested snapshots/deliverables and retained evidence are outside deletion.'}
    Write-LabJson $receiptPath $receipt
    & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath
} finally { $lease.Dispose() }
