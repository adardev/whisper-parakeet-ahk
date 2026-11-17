param(
    [Parameter(Mandatory = $true)] [string] $StockSettingsApk,
    [Parameter(Mandatory = $true)] [string] $OutputApk,
    [Parameter(Mandatory = $true)] [string] $ApktoolJar,
    [Parameter(Mandatory = $true)] [string] $DebugKeystore,
    [string] $AndroidSdk = $env:ANDROID_HOME,
    [string] $JavaExe = "$env:JAVA_HOME\bin\java.exe"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$expectedStockHash = 'd2708e14c50e8ec7f142774dfb000bebda1e94bdf4f209c8323746882155a93a'
foreach ($file in @($StockSettingsApk, $ApktoolJar, $DebugKeystore, $JavaExe)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required input not found: $file" }
}
if (-not $AndroidSdk) { throw 'Pass -AndroidSdk or set ANDROID_HOME.' }
if (Test-Path -LiteralPath $OutputApk) { throw "Output already exists: $OutputApk" }
$stockHash = (Get-FileHash -LiteralPath $StockSettingsApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($stockHash -ne $expectedStockHash) { throw "Unexpected SecSettings APK hash: $stockHash" }

$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools found under $AndroidSdk" }
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
foreach ($file in @($zipalign, $apksigner)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required Android tool not found: $file" }
}

$work = Join-Path ([IO.Path]::GetDirectoryName($OutputApk)) ('.settings-app-filter-' + [guid]::NewGuid().ToString('N'))
$decoded = Join-Path $work 'decoded'
$unsigned = Join-Path $work 'candidate-unsigned.apk'
$aligned = Join-Path $work 'candidate-aligned.apk'
New-Item -ItemType Directory -Path $work -Force | Out-Null
try {
    & $JavaExe -Xmx6g -jar $ApktoolJar d -f -r $StockSettingsApk -o $decoded --jobs 2
    if ($LASTEXITCODE -ne 0) { throw 'Could not decode the stock Settings APK.' }
    & (Join-Path $PSScriptRoot 'patch-settings-app-list.ps1') -DecodedRoot $decoded
    if ($LASTEXITCODE -ne 0) { throw 'Could not patch the installed-app list.' }

    & $JavaExe -Xmx6g -jar $ApktoolJar b $decoded --jobs 2 -o $unsigned
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $unsigned)) { throw 'Could not rebuild SecSettings.' }
    & $zipalign -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Could not zipalign the candidate.' }
    & $apksigner sign --ks $DebugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk $aligned
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $OutputApk)) { throw 'Could not sign the candidate.' }
    & $apksigner verify --verbose $OutputApk
    if ($LASTEXITCODE -ne 0) { throw 'Candidate signature verification failed.' }

    $candidateHash = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
    [pscustomobject]@{
        OutputApk = $OutputApk
        Package = 'com.android.settings'
        StockSha256 = $stockHash
        CandidateSha256 = $candidateHash
        Note = 'Runtime-only candidate. Do not install as an update; use the Zygote mount controller.'
    } | Format-List
} catch {
    Remove-Item -LiteralPath $OutputApk -Force -ErrorAction SilentlyContinue
    throw
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
