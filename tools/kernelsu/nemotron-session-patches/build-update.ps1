param(
    [Parameter(Mandatory = $true)][string]$CandidateApk,
    [string]$OutputZip = (Join-Path $env:TEMP 'nemotron-session-patches-v1.2.zip')
)
$ErrorActionPreference = 'Stop'
$module = $PSScriptRoot
$expected = '193b3d20f286c44b14284000d8406fab5e0b8e9a7275dc8718dfa2ddd5d8b96a'
$actual = (Get-FileHash -LiteralPath $CandidateApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw "Candidate hash mismatch: $actual" }
foreach ($name in @('boot-completed.sh','oneui-control.sh','honeyboard-control.sh','hotinstall.sh','customize.sh')) {
    $path = Join-Path $module $name
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing module file: $name" }
}
$stage = Join-Path $env:TEMP ('nemotron-session-patches-package-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
try {
    foreach ($name in @('module.prop','boot-completed.sh','oneui-control.sh','honeyboard-control.sh','install-update.sh','customize.sh','hotinstall.sh')) {
        Copy-Item -LiteralPath (Join-Path $module $name) -Destination $stage
    }
    Copy-Item -LiteralPath $CandidateApk -Destination (Join-Path $stage 'nemotron-oneui-home-root-candidate-v81.apk')
    $null = [System.Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem')
    if (Test-Path -LiteralPath $OutputZip) { Remove-Item -LiteralPath $OutputZip -Force }
    [System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $OutputZip, [System.IO.Compression.CompressionLevel]::NoCompression, $false)
} finally {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
Write-Output $OutputZip
