#requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$ReceiptPath, [switch]$Apply)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$receiptFile = Full-LabPath $ReceiptPath
Assert-LabOrdinary $receiptFile
$r = Read-LabJson $receiptFile
if ($r.schema_version -ne 1 -or $r.complete -isnot [bool] -or !$r.complete -or $r.status -ne 'COMPLETE') {
    throw 'The actual test workflow is not marked COMPLETE.'
}
if (@($r.pending_checks).Count -or !@($r.exit_codes).Count -or @($r.exit_codes | Where-Object { $_ -isnot [long] -and $_ -isnot [int] -or $_ -ne 0 }).Count) {
    throw 'Unfinished checks or failed/missing process exits prohibit cleanup.'
}
if (!@($r.inputs).Count -or !@($r.evidence).Count -or !@($r.cleanup_targets).Count -or !$r.basis) {
    throw 'Retained rebuild inputs, actual completion evidence and exact cleanup targets are required.'
}
$root = Assert-LabTarget $r.run_root $r.temporary_container @($r.protected_roots)
$targets = @()
foreach ($target in $r.cleanup_targets) {
    if ($target.kind -ne 'disposable-test-copy') { throw 'Target is not a disposable test copy.' }
    $t = Assert-LabTarget $target.path $r.temporary_container @($r.protected_roots)
    if ($t -ine $root -and !(Is-LabChild $t $root)) { throw 'Target is outside the recorded run.' }
    foreach ($prior in $targets) {
        if ($prior.path -ieq $t -or (Is-LabChild $prior.path $t) -or (Is-LabChild $t $prior.path)) { throw 'Duplicate or nested cleanup targets.' }
    }
    foreach ($kept in @($r.inputs) + @($r.evidence) + @(@{path=$receiptFile})) {
        $p = Full-LabPath $kept.path
        if ($p -ieq $t -or (Is-LabChild $p $t)) { throw 'Cleanup overlaps retained input or evidence.' }
    }
    $targets += @{path=$t; created_utc=$target.created_utc}
}
$mutex = [Threading.Mutex]::new($false, 'Local\Monarch-Minecraft-IsolatedValidation')
$held=$false
try {
    try { $held=$mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $held=$true }
    if (!$held) { throw 'A managed test is still using the local window.' }
    Assert-PriorLabLeaseIdle
    Assert-LabHashes @($r.inputs)
    Assert-LabHashes @($r.evidence)
    Assert-LabIdle @($targets.path) @($r.java_baseline) @($r.ports)
    $out = $receiptFile + '.cleanup.json'
    $receiptHash = (Get-FileHash -LiteralPath $receiptFile -Algorithm SHA256).Hash
    $previous=$null
    if (Test-Path -LiteralPath $out) {
        $previous=Read-LabJson $out
        if ($previous.receipt_sha256 -ine $receiptHash) { throw 'Cleanup output belongs to a different receipt.' }
    }
    $plan=@(); $gone=@()
    foreach ($t in $targets) {
        if (!(Test-Path -LiteralPath $t.path)) {
            if ($previous -and @($previous.removed | Where-Object { $_ -ieq $t.path }).Count) { $gone += $t.path; continue }
            throw "Target disappeared without this cleanup receipt: $($t.path)"
        }
        $item=Get-Item -LiteralPath $t.path -Force
        if (!$item.PSIsContainer -or $item.CreationTimeUtc.ToString('o') -cne $t.created_utc) { throw 'Target directory identity changed.' }
        $files=@(Get-LabInventory $t.path)
        $bytes=0L; foreach ($file in $files) { $bytes += $file.Length }
        $plan += @{path=$t.path; files=$files.Count; logical_bytes=$bytes}
    }
    $result=@{schema_version=1; receipt_sha256=$receiptHash; status='PLANNED'; targets=$plan; removed=$gone;
        logical_bytes=0L; free_before=@{}; free_after=@{}; checked_at=(Get-Date).ToUniversalTime().ToString('o')}
    foreach ($entry in $plan) { $result.logical_bytes += $entry.logical_bytes }
    if (!$Apply) {
        @{status='PLANNED'; targets=$plan; already_removed=$gone} | ConvertTo-Json -Depth 8
        return
    }
    foreach ($entry in $plan) {
        $driveRoot=[IO.Path]::GetPathRoot($entry.path)
        $result.free_before[$driveRoot]=[IO.DriveInfo]::new($driveRoot).AvailableFreeSpace
    }
    $result.status='CLEANING'; Write-LabJson $out $result
    foreach ($entry in $plan) {
        # Resolved absolute targets were checked against the explicit container and protected paths above.
        # Recheck ordinary ancestors/tree and active processes immediately before native PowerShell deletion.
        Assert-LabOrdinary $entry.path
        $null=@(Get-LabInventory $entry.path)
        Assert-LabIdle @($entry.path) @($r.java_baseline) @($r.ports)
        Remove-Item -LiteralPath $entry.path -Recurse -Force
        if (Test-Path -LiteralPath $entry.path) { throw 'Removal was incomplete.' }
        $result.removed += $entry.path
        Write-LabJson $out $result
    }
    foreach ($driveRoot in $result.free_before.Keys) { $result.free_after[$driveRoot]=[IO.DriveInfo]::new($driveRoot).AvailableFreeSpace }
    $result.status='CLEANED'; $result.finished_at=(Get-Date).ToUniversalTime().ToString('o')
    Write-LabJson $out $result
    @{status='CLEANED'; removed=$result.removed; logical_bytes=$result.logical_bytes; report=$out} | ConvertTo-Json -Depth 5
} finally {
    if ($held) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
