Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)

function Read-LabJson([string]$Path) {
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding utf8
    if ((Get-Command ConvertFrom-Json).Parameters.ContainsKey('DateKind')) {
        return $raw | ConvertFrom-Json -AsHashtable -DateKind String
    }
    return $raw | ConvertFrom-Json -AsHashtable
}
function Write-LabJson([string]$Path, $Value) {
    $text = $Value | ConvertTo-Json -Depth 30
    [IO.File]::WriteAllText($Path, $text + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}
function Full-LabPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or ![IO.Path]::IsPathFullyQualified($Path)) {
        throw 'An absolute local filesystem path is required.'
    }
    $p = [IO.Path]::GetFullPath($Path).TrimEnd('\','/')
    if ($p.StartsWith('\\') -or $p -eq [IO.Path]::GetPathRoot($p).TrimEnd('\','/')) {
        throw 'Drive roots and network shares are outside this local test helper.'
    }
    return $p
}
function Is-LabChild([string]$Child, [string]$Parent) {
    return $Child.StartsWith($Parent.TrimEnd('\','/') + '\', [StringComparison]::OrdinalIgnoreCase)
}
function Assert-LabOrdinary([string]$Path) {
    $walk = $Path
    while ($walk) {
        if (Test-Path -LiteralPath $walk) {
            $item = Get-Item -LiteralPath $walk -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point: $walk" }
        }
        $parent = [IO.Path]::GetDirectoryName($walk)
        if (!$parent -or $parent -eq $walk) { break }
        $walk = $parent
    }
}
function Assert-LabTarget([string]$Target, [string]$Container, [string[]]$Protected) {
    $t = Full-LabPath $Target
    $c = Full-LabPath $Container
    if (!(Is-LabChild $t $c)) { throw "Target is not a strict child of the explicit container: $t" }
    $denied = @('E:\Minecarft','D:\MSLX','D:\NAST', (Join-Path $env:USERPROFILE '.codex\skills')) + @($Protected)
    foreach ($p in $denied) {
        if (!$p) { continue }
        $p = Full-LabPath $p
        if ($t -ieq $p -or (Is-LabChild $t $p) -or (Is-LabChild $p $t)) {
            throw "Target overlaps a protected path: $t"
        }
    }
    Assert-LabOrdinary $c
    Assert-LabOrdinary $t
    return $t
}
function Get-LabInventory([string]$Path) {
    $stack = [Collections.Generic.Stack[string]]::new()
    $stack.Push($Path)
    $files = [Collections.Generic.List[object]]::new()
    while ($stack.Count) {
        $dir = $stack.Pop()
        foreach ($item in Get-ChildItem -LiteralPath $dir -Force) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point: $($item.FullName)" }
            if ($item.PSIsContainer) { $stack.Push($item.FullName) }
            else { $files.Add($item) }
        }
    }
    return $files.ToArray()
}
function Get-LabJavaIdentity {
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        ForEach-Object { @{pid=[int]$_.ProcessId; created=$_.CreationDate.ToUniversalTime().ToString('o')} })
}
function Assert-LabIdle([string[]]$Paths, [object[]]$JavaBaseline, [int[]]$Ports) {
    $processes = @(Get-CimInstance Win32_Process)
    foreach ($process in $processes) {
        if ($process.ProcessId -eq $PID -or !$process.CommandLine) { continue }
        $command = $process.CommandLine.Replace('/','\')
        foreach ($path in $Paths) {
            if ($command.IndexOf($path.Replace('/','\'), [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                throw "A process still refers to the test target; PID=$($process.ProcessId)"
            }
        }
        if ($process.Name -in @('java.exe','javaw.exe')) {
            $stamp = $process.CreationDate.ToUniversalTime().ToString('o')
            $known = @($JavaBaseline | Where-Object { [int]$_.pid -eq $process.ProcessId -and $_.created -eq $stamp })
            if (!$known.Count) { throw "Unaccounted Java process; verify test ownership before continuing. PID=$($process.ProcessId)" }
        }
    }
    foreach ($port in $Ports) {
        if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid test port.' }
        if (@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).Count) {
            throw "Test port is still occupied: $port"
        }
    }
}
function Assert-LabHashes([object[]]$Files) {
    foreach ($file in $Files) {
        $p = Full-LabPath ([string]$file.path)
        Assert-LabOrdinary $p
        if (!(Test-Path -LiteralPath $p -PathType Leaf) -or $file.sha256 -notmatch '^[a-fA-F0-9]{64}$') {
            throw "Missing retained file or invalid SHA256: $p"
        }
        if ((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash -ine $file.sha256) {
            throw "Retained file hash changed: $p"
        }
    }
}

function Get-LabLeasePath {
    $dir = Join-Path $env:LOCALAPPDATA 'Codex\minecraft-validation'
    Assert-LabOrdinary $dir
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    return (Join-Path $dir 'window.json')
}
function Assert-PriorLabLeaseIdle {
    $file = Get-LabLeasePath
    if (Test-Path -LiteralPath $file) {
        $lease = Read-LabJson $file
        if ($lease.status -ne 'RELEASED') {
            Assert-LabIdle @((Full-LabPath $lease.run_root)) @($lease.java_baseline) @($lease.ports)
        }
    }
}
