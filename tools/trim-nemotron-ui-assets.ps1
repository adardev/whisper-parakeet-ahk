param(
    [Parameter(Mandatory=$true)][string]$DecodedRoot,
    [Parameter(Mandatory=$true)][string]$KeyboardReferenceApk,
    [Parameter(Mandatory=$true)][string]$HomeReferenceApk
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=[IO.Path]::GetFullPath($DecodedRoot).TrimEnd('\')
$keyboard=[IO.Compression.ZipFile]::OpenRead($KeyboardReferenceApk)
$homeZip=[IO.Compression.ZipFile]::OpenRead($HomeReferenceApk)
$remove=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$homeEntries=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
try {
    foreach ($entry in $homeZip.Entries) { [void]$homeEntries.Add($entry.FullName) }
    foreach ($entry in $keyboard.Entries) {
        if (($entry.FullName.StartsWith('assets/') -or $entry.FullName.StartsWith('lib/')) -and
            -not $homeEntries.Contains($entry.FullName)) { [void]$remove.Add($entry.FullName) }
    }
} finally { $keyboard.Dispose(); $homeZip.Dispose() }
$modelDirectory=Join-Path $root 'assets\whisper'
if (Test-Path -LiteralPath $modelDirectory) {
    foreach ($file in Get-ChildItem -LiteralPath $modelDirectory -File -Recurse) {
        [void]$remove.Add([IO.Path]::GetRelativePath($root,$file.FullName).Replace('\','/'))
    }
}
$removedBytes=0L
foreach ($relative in $remove) {
    $path=[IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $path.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Asset escaped decoded root' }
    if ([IO.File]::Exists($path)) {
        $removedBytes += [IO.FileInfo]::new($path).Length
        [IO.File]::Delete($path)
    }
}
Write-Output "UI removed unused keyboard assets/libraries: $removedBytes bytes"
