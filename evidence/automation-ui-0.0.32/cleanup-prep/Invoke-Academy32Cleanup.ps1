#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Inspect','Plan','Apply')][string]$Mode = 'Inspect',
    [string]$RetainedRoot = 'D:\Minecraft-Validation\receipts\academy-automation32-20261025',
    [string]$ReviewedFactsPath,
    [ValidatePattern('^gt-r[1-9][0-9]*$')][string]$FinalGameTestRun = 'gt-r1',
    [ValidatePattern('^client-r[1-9][0-9]*$')][string]$FinalClientRun = 'client-r1'
)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$runRoot = 'D:\Minecraft-Validation\runs\academy-automation32-20261025'
$container = 'D:\Minecraft-Validation\runs'
$retained = Full-LabPath $RetainedRoot
$receiptPath = Join-Path $retained 'completion.json'
if ($env:COMPUTERNAME -ine 'VICERACH') { throw 'This prepared cleanup is restricted to VICERACH.' }
if ($retained -ine 'D:\Minecraft-Validation\receipts\academy-automation32-20261025') {
    throw 'Retained archive must use the reviewed exact receipts path outside the disposable run.'
}
$protected = @('D:\Minecraft-Validation\bases','D:\Minecraft-Validation\control',
    'D:\Minecraft-Validation\receipts','D:\Minecraft-Validation\inputs','D:\Minecraft-Validation\cache',
    'D:\Minecraft-Projects',(Join-Path $env:USERPROFILE '.gradle'))
$null = Assert-LabTarget $runRoot $container $protected
Assert-LabOrdinary $retained

if ($Mode -eq 'Inspect') {
    $identity = Get-Item -LiteralPath $runRoot -Force
    $active = @(Get-CimInstance Win32_Process | Where-Object {
        $_.ProcessId -ne $PID -and $_.CommandLine -and
        $_.CommandLine.Replace('/','\').IndexOf($runRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0
    } | ForEach-Object { @{pid=[int]$_.ProcessId; name=$_.Name; created=$_.CreationDate.ToUniversalTime().ToString('o')} })
    @{host=$env:COMPUTERNAME; run_root=$runRoot; created_utc=$identity.CreationTimeUtc.ToString('o');
      inspected_utc=(Get-Date).ToUniversalTime().ToString('o'); java_baseline=@(Get-LabJavaIdentity);
      processes_referencing_run=$active; final_runs=@($FinalGameTestRun,$FinalClientRun)} | ConvertTo-Json -Depth 8
    return
}

# Use the same exclusive file lease as the actual Vicerach runners, in addition
# to the generic cleanup helper's process checks and validation mutex.
$lease = [IO.File]::Open('D:\Minecraft-Validation\control\client-window.lock','OpenOrCreate','ReadWrite','None')
try {
    if ($Mode -eq 'Apply') {
        $receipt = Read-LabJson $receiptPath
        if ($receipt.run_root -ine $runRoot -or @($receipt.cleanup_targets).Count -ne 1 -or
            $receipt.cleanup_targets[0].path -ine $runRoot) { throw 'Prepared receipt targets changed.' }
        # Reuse the sealed receipt. Never regenerate timestamps, exits or input
        # hashes between review and Apply.
        & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath -Apply
        return
    }

    if (!$ReviewedFactsPath) { throw 'Plan requires the reviewed Inspect JSON via -ReviewedFactsPath.' }
    $factsFile = Full-LabPath $ReviewedFactsPath
    Assert-LabOrdinary $factsFile
    $facts = Read-LabJson $factsFile
    if ($facts.host -ine 'VICERACH' -or $facts.run_root -ine $runRoot) { throw 'Inspect receipt identifies a different host/run.' }
    $identity = Get-Item -LiteralPath $runRoot -Force
    if ($identity.CreationTimeUtc.ToString('o') -cne $facts.created_utc) { throw 'Run directory creation identity changed since inspection.' }
    Assert-LabIdle @($runRoot) @($facts.java_baseline) @()
    if (Test-Path -LiteralPath $receiptPath) { throw 'completion.json already exists; review/use Apply without overwriting its identity.' }

    # Root must first upload the sealed final-validation.json from Monarch.
    # This is an exact byte copy, not a replacement for the retained local archive.
    $finalPath = Join-Path $retained 'final-validation.json'
    $final = Read-LabJson $finalPath
    if ($final.status -ne 'PASS' -or $final.version -ne '0.0.32-scex-automation-ui') { throw 'Sealed final validation is not PASS for this version.' }
    $finalRuns = @(@{name=$FinalGameTestRun; alias='gametests-final'}, @{name=$FinalClientRun; alias='client-final'})
    $exitCodes = @()
    foreach ($run in $finalRuns) {
        $result = Read-LabJson (Join-Path $runRoot ('runs\' + $run.name + '\result.json'))
        if (!$result.passed -or !$result.process_exited -or $result.forced -or $result.exit_code -ne 0) {
            throw "Final run has not passed with a normal process exit: $($run.name)"
        }
        if ($run.alias -eq 'client-final' -and !$result.saved_after_stop) { throw 'Final client has no post-stop save proof.' }
        $exitCodes += [int]$result.exit_code
    }

    function Retain-File([string]$Source,[string]$Destination) {
        Assert-LabOrdinary $Source
        Assert-LabOrdinary $Destination
        $sha = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
        if (Test-Path -LiteralPath $Destination) {
            if ((Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash -ine $sha) {
                throw "Existing retained file has different bytes: $Destination"
            }
        } else {
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Destination)) | Out-Null
            Copy-Item -LiteralPath $Source -Destination $Destination
        }
        return @{path=(Full-LabPath $Destination); sha256=$sha}
    }

    $evidence = @(@{path=(Full-LabPath $finalPath); sha256=(Get-FileHash -LiteralPath $finalPath -Algorithm SHA256).Hash})
    $evidence += Retain-File $factsFile (Join-Path $retained 'reviewed-facts.json')
    $history = @()
    $keptNames = @('result.json','process.json','artifacts.json','derived-launch.json','memory-samples.json',
        'academy-automation-ui-client.txt','academy-automation-ui-save-checkpoint.txt','shutdown-threads.txt','stop-request.txt',
        'logs\latest.log','stdout.log','stderr.log')
    $runDirectories = @(Get-ChildItem -LiteralPath (Join-Path $runRoot 'runs') -Directory |
        Where-Object { $_.Name -match '^(gt|client)-r[1-9][0-9]*$' } | Sort-Object Name)
    foreach ($dir in $runDirectories) {
        Assert-LabOrdinary $dir.FullName
        $selected = @($finalRuns | Where-Object { $_.name -eq $dir.Name })
        $archiveName = if ($selected.Count) { $selected[0].alias } else { 'history\' + $dir.Name }
        foreach ($relative in $keptNames) {
            $source = Join-Path $dir.FullName $relative
            if (Test-Path -LiteralPath $source -PathType Leaf) {
                $evidence += Retain-File $source (Join-Path (Join-Path $retained $archiveName) $relative)
            }
        }
        $resultFile = Join-Path $dir.FullName 'result.json'
        # A missing historical exit remains null. A failed attempt is not
        # rewritten to zero merely because its successor passed.
        $actual = if (Test-Path -LiteralPath $resultFile) { Read-LabJson $resultFile } else { $null }
        $history += @{run=$dir.Name; role=$(if($selected.Count){'final-acceptance'}else{'superseded-attempt'});
            result=$actual; result_missing=($null -eq $actual); archive=Join-Path $retained $archiveName}
    }

    # Retain only the two final tested inputs, not every historical version.
    # The source tree, final distributable JAR/source ZIP and authoritative
    # evidence remain on Monarch as recorded in final-validation.json.
    $inputs = @(@{path='D:\Minecraft-Validation\bases\academy-ssid23-small-20260908\manifest.json';
        sha256='031019b874738c2aba2723c15b9e8b9e2ccefdd9d31f951909c9f44d505e033b'})
    $stem = 'AcademyCraft-neoforge-1.21.1-0.0.32-scex-automation-ui'
    $jarNames = @(($stem + '.jar'), ($stem + '-audit-tests.jar'))
    foreach ($name in $jarNames) {
        $serverJar = Join-Path $runRoot ('runs\' + $FinalGameTestRun + '\mods\' + $name)
        $clientJar = Join-Path $runRoot ('runs\' + $FinalClientRun + '\mods\' + $name)
        $hash = (Get-FileHash -LiteralPath $serverJar -Algorithm SHA256).Hash
        if ((Get-FileHash -LiteralPath $clientJar -Algorithm SHA256).Hash -ine $hash) { throw 'Final server/client tested inputs differ.' }
        $sealed = @($final.artifact_entry_comparisons | Where-Object { [IO.Path]::GetFileName($_.tested) -eq $name })
        if ($sealed.Count -ne 1 -or $sealed[0].tested_sha256 -ine $hash -or !$sealed[0].runtime_entries_identical) {
            throw 'Tested input does not match the sealed final validation.'
        }
        $inputs += Retain-File $serverJar (Join-Path (Join-Path $retained 'tested-inputs') $name)
    }
    Assert-LabHashes $inputs
    Assert-LabHashes $evidence
    $receipt = Read-LabJson (Join-Path $PSScriptRoot 'completion.template.json')
    $receipt.complete = $true; $receipt.status = 'COMPLETE'; $receipt.pending_checks = @()
    $receipt.exit_codes = $exitCodes
    $receipt.final_runs = @($FinalGameTestRun,$FinalClientRun)
    $receipt.attempt_history = $history
    $receipt.inputs = $inputs; $receipt.evidence = $evidence
    $receipt.java_baseline = @($facts.java_baseline)
    $receipt.protected_roots = $protected
    $receipt.cleanup_targets[0].created_utc = $facts.created_utc
    $receipt.basis = 'Final selected GameTest and client runs passed with normal zero exits, final client saved after stop, and sealed final-validation.json is PASS. Historical failures/missing exits remain recorded verbatim in attempt_history and retained files. Exact final tested inputs and all bounded execution evidence are retained outside the disposable run; authoritative source/deliverables/evidence remain on Monarch.'
    Write-LabJson $receiptPath $receipt
    & (Join-Path $PSScriptRoot 'Remove-CompletedTestRun.ps1') -ReceiptPath $receiptPath
} finally { $lease.Dispose() }
