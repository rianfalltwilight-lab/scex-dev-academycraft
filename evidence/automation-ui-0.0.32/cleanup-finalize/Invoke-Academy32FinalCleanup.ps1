#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('MONARCH','VICERACH')][string]$HostScope,
    [ValidateSet('Inspect','Plan','Apply')][string]$Mode = 'Inspect',
    [string]$ReviewedFactsPath,
    [ValidatePattern('^gt-r[1-9][0-9]*$')][string]$FinalGameTestRun = 'gt-r1',
    [ValidatePattern('^client-r[1-9][0-9]*$')][string]$FinalClientRun = 'client-r1'
)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$project = 'D:\Minecraft-Projects\academycraft-deep-audit-0.0.29-20261025'
$localRoot = 'D:\SCEX-Work\01-Mods\Other-Ports\AcademyCraft\0.0.32-20261025'
$remoteRoot = 'D:\Minecraft-Validation\runs\academy-automation32-20261025'
$originalBase = 'D:\Minecraft-Validation\bases\academy-ssid23-small-20260908'
$manifestSha = '031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'
$localEvidence = Join-Path $project 'evidence\automation-ui-0.0.32'
$runRoot = if ($HostScope -eq 'MONARCH') { $localRoot } else { $remoteRoot }
$container = if ($HostScope -eq 'MONARCH') { 'D:\SCEX-Work\01-Mods\Other-Ports\AcademyCraft' } else { 'D:\Minecraft-Validation\runs' }
$retained = if ($HostScope -eq 'MONARCH') { Join-Path $localEvidence 'cleanup-finalize\monarch' } else { 'D:\Minecraft-Validation\receipts\academy-automation32-20261025' }
$sourceEvidence = if ($HostScope -eq 'MONARCH') { $localEvidence } else { $retained }
$receiptPath = Join-Path $retained 'completion.json'
$protected = @('D:\Minecraft-Projects','D:\Minecraft-Validation\bases','D:\Minecraft-Validation\receipts',
    'D:\Minecraft-Validation\control','D:\Minecraft-Validation\inputs','D:\Minecraft-Validation\cache',
    (Join-Path $env:USERPROFILE '.gradle'))
if ($env:COMPUTERNAME -ine $HostScope) { throw 'Wrong host for the fixed cleanup scope.' }
$null = Assert-LabTarget $runRoot $container $protected
Assert-LabOrdinary $retained

function Retain-File([string]$Source, [string]$Destination) {
    $Source = Full-LabPath $Source; $Destination = Full-LabPath $Destination
    Assert-LabOrdinary $Source; Assert-LabOrdinary $Destination
    if ($Destination -ieq $runRoot -or (Is-LabChild $Destination $runRoot)) { throw 'Evidence destination overlaps cleanup.' }
    $sha = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
    if (Test-Path -LiteralPath $Destination) {
        if ((Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash -ine $sha) { throw "Retained bytes differ: $Destination" }
    } else {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Destination)) | Out-Null
        Copy-Item -LiteralPath $Source -Destination $Destination
    }
    return @{path=$Destination; sha256=$sha}
}

function Assert-RecordedProcessesExited {
    $runs = Join-Path $runRoot 'runs'
    if (!(Test-Path -LiteralPath $runs)) { return }
    foreach ($dir in Get-ChildItem -LiteralPath $runs -Directory) {
        $processFile = Join-Path $dir.FullName 'process.json'
        if ($HostScope -eq 'VICERACH') {
            if ((Test-Path -LiteralPath $processFile) -or (Test-Path -LiteralPath (Join-Path $dir.FullName 'result.json'))) {
                throw 'Remote staging contains runtime receipts; NOT_STARTED no longer applies. Preserve and review it.'
            }
        } elseif (Test-Path -LiteralPath $processFile) {
            $p = Read-LabJson $processFile
            $live = Get-Process -Id ([int]$p.pid) -ErrorAction SilentlyContinue
            if ($live -and $live.StartTime.ToUniversalTime() -eq ([DateTimeOffset]::Parse($p.started)).UtcDateTime) {
                throw "Recorded test process is still running: PID=$($p.pid)"
            }
        }
    }
}

if ($Mode -eq 'Inspect') {
    $identity = Get-Item -LiteralPath $runRoot -Force
    $active = @(Get-CimInstance Win32_Process | Where-Object {
        $_.ProcessId -ne $PID -and $_.CommandLine -and
        $_.CommandLine.Replace('/','\').IndexOf($runRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0
    } | ForEach-Object { @{pid=[int]$_.ProcessId; name=$_.Name; created=$_.CreationDate.ToUniversalTime().ToString('o')} })
    @{host=$HostScope; run_root=$runRoot; created_utc=$identity.CreationTimeUtc.ToString('o');
      inspected_utc=(Get-Date).ToUniversalTime().ToString('o'); java_baseline=@(Get-LabJavaIdentity);
      processes_referencing_run=$active} | ConvertTo-Json -Depth 8
    return
}

# Same lease used by both actual runners; the unchanged helper also checks its mutex.
$lease = [IO.File]::Open('D:\Minecraft-Validation\control\client-window.lock','OpenOrCreate','ReadWrite','None')
try {
    Assert-RecordedProcessesExited
    if ($Mode -eq 'Apply') {
        $receipt = Read-LabJson $receiptPath
        if ($receipt.host -ine $HostScope -or $receipt.run_root -ine $runRoot -or
            $receipt.temporary_container -ine $container -or @($receipt.cleanup_targets).Count -ne 1 -or
            $receipt.cleanup_targets[0].path -ine $runRoot) { throw 'Sealed receipt target differs from the fixed scope.' }
        & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath -Apply
        return
    }
    if (!$ReviewedFactsPath) { throw 'Plan needs reviewed Inspect JSON via -ReviewedFactsPath.' }
    $factsFile = Full-LabPath $ReviewedFactsPath
    $facts = Read-LabJson $factsFile
    if ($facts.host -ine $HostScope -or $facts.run_root -ine $runRoot) { throw 'Inspect identifies a different host/run.' }
    $identity = Get-Item -LiteralPath $runRoot -Force
    if ($facts.created_utc -cne $identity.CreationTimeUtc.ToString('o')) { throw 'Directory identity changed after Inspect.' }
    Assert-LabIdle @($runRoot) @($facts.java_baseline) @()
    if (Test-Path -LiteralPath $receiptPath) { throw 'A sealed completion already exists; review it and use Apply, without rewriting it.' }

    $finalFile = Join-Path $sourceEvidence 'final-validation.json'
    $final = Read-LabJson $finalFile
    if ($final.status -ne 'PASS' -or $final.version -ne '0.0.32-scex-automation-ui') { throw 'Final validation is not PASS for this version.' }
    $finalRuns = @(@{name=$FinalGameTestRun; alias='gametests-final'}, @{name=$FinalClientRun; alias='client-final'})
    $evidence = @(Retain-File $finalFile $finalFile)
    $exitCodes = @(); $exitSources = @()
    foreach ($run in $finalRuns) {
        $folder = Join-Path $sourceEvidence $run.alias
        $resultFile = Join-Path $folder 'result.json'; $result = Read-LabJson $resultFile
        if ($result.host -ine 'MONARCH' -or !$result.passed -or !$result.process_exited -or $result.forced -or $result.exit_code -ne 0) {
            throw "No actual normal successful Monarch exit for $($run.alias)."
        }
        if ($run.alias -eq 'client-final' -and !$result.saved_after_stop) { throw 'Client post-stop save proof missing.' }
        $exitCodes += [int]$result.exit_code
        $exitSources += @{host='MONARCH'; run=$run.name; result=$resultFile; exit_code=[int]$result.exit_code}
        foreach ($file in Get-LabInventory $folder) { $evidence += Retain-File $file.FullName $file.FullName }
        if ($HostScope -eq 'MONARCH') {
            $actual = Join-Path $runRoot ('runs\' + $run.name + '\result.json')
            if ((Get-FileHash -LiteralPath $actual).Hash -ine (Get-FileHash -LiteralPath $resultFile).Hash) { throw 'Final alias differs from the selected actual run receipt.' }
        }
    }
    foreach ($name in @('monarch-preflight.json','preflight.json','vicerach-prelaunch-block.json')) {
        $file = Join-Path $sourceEvidence $name; $evidence += Retain-File $file $file
    }
    $evidence += Retain-File $factsFile (Join-Path $retained 'reviewed-facts.json')
    $block = Read-LabJson (Join-Path $sourceEvidence 'vicerach-prelaunch-block.json')
    if ($block.status -ne 'NOT_STARTED_RESOURCE_GUARD' -or $block.test_java_started -ne $false) { throw 'Remote non-start receipt is missing or changed.' }
    $inputs = @(); $history = @()
    if ($HostScope -eq 'MONARCH') {
        $inputs += Retain-File (Join-Path $runRoot 'base\manifest.json') (Join-Path $retained 'base-manifest.json')
        if ($inputs[0].sha256 -ine $manifestSha) { throw 'Copied base manifest differs from the frozen Vicerach source.' }
        $evidence += Retain-File (Join-Path $runRoot 'base\base-receipt.json') (Join-Path $retained 'base-receipt.json')
        foreach ($comparison in $final.artifact_entry_comparisons) {
            $path = Full-LabPath $comparison.tested
            if (!(Is-LabChild $path (Join-Path $project 'build')) -or !$comparison.runtime_entries_identical) { throw 'Tested artifact is outside retained project build inputs.' }
            if ((Get-FileHash -LiteralPath $path).Hash -ine $comparison.tested_sha256) { throw 'External tested artifact bytes changed.' }
            $inputs += @{path=$path; sha256=$comparison.tested_sha256}
            foreach ($run in $finalRuns) {
                $runtimeJar = Join-Path $runRoot ('runs\' + $run.name + '\mods\' + [IO.Path]::GetFileName($path))
                if ((Get-FileHash -LiteralPath $runtimeJar).Hash -ine $comparison.tested_sha256) { throw 'Final actual runtime JAR differs from the external tested snapshot.' }
            }
        }
        if ($inputs.Count -ne 3) { throw 'Expected base manifest plus the final release and probe snapshot inputs.' }
        foreach ($dir in Get-ChildItem -LiteralPath (Join-Path $runRoot 'runs') -Directory | Sort-Object Name) {
            $resultFile = Join-Path $dir.FullName 'result.json'
            $actual = if (Test-Path -LiteralPath $resultFile) { Read-LabJson $resultFile } else { $null }
            $selected = @($finalRuns | Where-Object { $_.name -eq $dir.Name }).Count -gt 0
            $history += @{host='MONARCH'; run=$dir.Name; role=$(if ($selected) {'final-acceptance'} else {'superseded-attempt'}); result=$actual; result_missing=($null -eq $actual)}
            if (!$selected) {
                foreach ($file in Get-LabInventory $dir.FullName) {
                    $relative = [IO.Path]::GetRelativePath($dir.FullName, $file.FullName)
                    if ($relative -match '^(logs|screenshots)\\' -or ($relative -notmatch '\\' -and $file.Extension -in @('.json','.txt','.log','.png'))) {
                        $evidence += Retain-File $file.FullName (Join-Path $retained ('history\' + $dir.Name + '\' + $relative))
                    }
                }
            }
        }
    } else {
        # This staging never ran Java. Its old JARs are NOT the final tested inputs.
        # Final input snapshots stay on Monarch; the original frozen base stays here.
        $inputs += @{path=(Join-Path $originalBase 'manifest.json'); sha256=$manifestSha}
        $monarchFile = Join-Path $retained 'monarch-completion.json'; $monarch = Read-LabJson $monarchFile
        if ($monarch.status -ne 'COMPLETE' -or !$monarch.complete -or $monarch.host -ine 'MONARCH' -or
                $monarch.run_root -ine $localRoot) { throw 'Completed actual Monarch workflow receipt missing.' }
        $sealedFinal = @($monarch.evidence | Where-Object { [IO.Path]::GetFileName($_.path) -eq 'final-validation.json' })
        if ($sealedFinal.Count -ne 1 -or $sealedFinal[0].sha256 -ine (Get-FileHash -LiteralPath $finalFile).Hash) {
            throw 'Remote final-validation copy differs from the sealed Monarch completion evidence.'
        }
        $evidence += Retain-File $monarchFile $monarchFile
        $history = @($monarch.attempt_history)
        $staging = @(Get-LabInventory $runRoot | ForEach-Object {
            @{relative_path=[IO.Path]::GetRelativePath($runRoot,$_.FullName); bytes=$_.Length;
              sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
        })
        $stageFile = Join-Path $retained 'unstarted-staging-identity.json'
        Write-LabJson $stageFile @{status='NOT_STARTED_RESOURCE_GUARD'; test_java_started=$false;
            runtime_exit_code=$null; files=$staging; final_runtime_host='MONARCH'}
        $evidence += Retain-File $stageFile $stageFile
    }
    if (!@($history | Where-Object { $_.host -eq 'VICERACH' -and $_.role -eq 'unstarted-staging' }).Count) {
        $history += @{host='VICERACH'; role='unstarted-staging'; status='NOT_STARTED_RESOURCE_GUARD'; test_java_started=$false; exit_code=$null; result=$block}
    }
    Assert-LabHashes $inputs; Assert-LabHashes $evidence
    $receipt = @{schema_version=1; run_id='academy-automation32-20261025'; host=$HostScope;
        run_root=$runRoot; temporary_container=$container; complete=$true; status='COMPLETE'; pending_checks=@();
        exit_codes=$exitCodes; exit_sources=$exitSources; final_runs=@($FinalGameTestRun,$FinalClientRun);
        attempt_history=$history; inputs=$inputs; evidence=$evidence; java_baseline=@($facts.java_baseline);
        ports=@(); protected_roots=$protected; cleanup_targets=@(@{kind='disposable-test-copy'; path=$runRoot; created_utc=$facts.created_utc});
        base_origin=@{host='VICERACH'; path=$originalBase; manifest_sha256=$manifestSha; preserved=$true};
        final_inputs_host='MONARCH'; final_inputs=@($final.artifact_entry_comparisons);
        basis='Overall checks completed on MONARCH with actual final normal zero exits and post-stop client save. All historical results remain unchanged. VICERACH staging never started Java (NOT_STARTED_RESOURCE_GUARD, exit=null); its cleanup relies on the completed MONARCH workflow, not a fabricated remote runtime pass. Final tested artifacts, source and delivery files remain in the external MONARCH project; original VICERACH base and required evidence are retained.'}
    Write-LabJson $receiptPath $receipt
    & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath
} finally { $lease.Dispose() }
