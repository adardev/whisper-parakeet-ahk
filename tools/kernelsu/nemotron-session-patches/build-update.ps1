param(
    [Parameter(Mandatory = $true)][string]$CandidateApk,
    [Parameter(Mandatory = $true)][string]$SettingsCandidateApk,
    [Parameter(Mandatory = $true)][string]$DialerCandidateApk,
    [string]$AndroidAutoCandidateApk = (Join-Path $PSScriptRoot '..\..\..\artifacts\android-auto-rail\android-auto-18.1.164344-rail-1dp.apk'),
    [string]$OutputZip = (Join-Path $env:TEMP 'nemotron-session-patches-v1.9.4.zip')
)
$ErrorActionPreference = 'Stop'
$module = $PSScriptRoot
$expected = '406a11a4484c338a2fa2419cc9ca9df25668d6ca9070f828ec4f679342e834c5'
$actual = (Get-FileHash -LiteralPath $CandidateApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw "Candidate hash mismatch: $actual" }
$settingsExpected = 'afcc203b80e8278c7000a17cd0651e37ba3b96aa9673d11fafeaee28b8137d6f'
$settingsActual = (Get-FileHash -LiteralPath $SettingsCandidateApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($settingsActual -ne $settingsExpected) { throw "Settings candidate hash mismatch: $settingsActual" }
$dialerExpected = 'f90284b44047fb60817cb2911fbb3cb1aba9ee009fa272db920827b8793e60e5'
$dialerActual = (Get-FileHash -LiteralPath $DialerCandidateApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($dialerActual -ne $dialerExpected) { throw "Samsung Phone candidate hash mismatch: $dialerActual" }
$androidAutoExpected = '6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8'
$androidAutoActual = (Get-FileHash -LiteralPath $AndroidAutoCandidateApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($androidAutoActual -ne $androidAutoExpected) { throw "Android Auto candidate hash mismatch: $androidAutoActual" }
foreach ($name in @('boot-completed.sh','service.sh','oneui-control.sh','honeyboard-control.sh','waze-control.sh','dialer-control.sh','android-auto-rail-control.sh','hotinstall.sh','customize.sh')) {
    $path = Join-Path $module $name
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing module file: $name" }
}
$settingsControl = Join-Path $module '..\settings-app-filter\control.sh'
if (-not (Test-Path -LiteralPath $settingsControl)) { throw 'Missing Settings app filter controller.' }
$stage = Join-Path $env:TEMP ('nemotron-session-patches-package-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
try {
    foreach ($name in @('module.prop','boot-completed.sh','service.sh','oneui-control.sh','honeyboard-control.sh','waze-control.sh','dialer-control.sh','android-auto-rail-control.sh','install-update.sh','customize.sh','hotinstall.sh')) {
        Copy-Item -LiteralPath (Join-Path $module $name) -Destination $stage
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\settings-app-filter\control.sh') -Destination (Join-Path $stage 'settings-app-filter-control.sh')
    Copy-Item -LiteralPath $CandidateApk -Destination (Join-Path $stage 'nemotron-oneui-home-root-candidate-home100ms.apk')
    Copy-Item -LiteralPath $SettingsCandidateApk -Destination (Join-Path $stage 'nemotron-settings-app-filter-v1.apk')
    Copy-Item -LiteralPath $DialerCandidateApk -Destination (Join-Path $stage 'nemotron-samsung-dialer-fullscreen.apk')
    Copy-Item -LiteralPath $AndroidAutoCandidateApk -Destination (Join-Path $stage 'nemotron-aa-18_1-rail1dp.apk')
    $null = [System.Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem')
    if (Test-Path -LiteralPath $OutputZip) { Remove-Item -LiteralPath $OutputZip -Force }
    [System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $OutputZip, [System.IO.Compression.CompressionLevel]::NoCompression, $false)
} finally {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
Write-Output $OutputZip
