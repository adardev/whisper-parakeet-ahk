param(
    [Parameter(Mandatory = $true)] [string] $StockOneUiApk,
    [Parameter(Mandatory = $true)] [string] $OutputApk,
    [Parameter(Mandatory = $true)] [string] $ApktoolJar,
    [Parameter(Mandatory = $true)] [string] $FrameworkDir,
    [Parameter(Mandatory = $true)] [string] $DebugKeystore,
    [string] $JavaExe = "$env:JAVA_HOME\bin\java.exe",
    [string] $AndroidSdk = "$env:ANDROID_HOME"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
foreach ($file in @($StockOneUiApk, $ApktoolJar, $FrameworkDir, $DebugKeystore, $JavaExe)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required input not found: $file" }
}
if (Test-Path -LiteralPath $OutputApk) { throw "Output already exists: $OutputApk" }
if (-not $AndroidSdk) { throw 'ANDROID_HOME must point to the Android SDK.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools found under $AndroidSdk" }
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$d8 = Join-Path $buildTools.FullName 'd8.bat'
$androidJar = Join-Path $AndroidSdk 'platforms\android-36\android.jar'
foreach ($file in @($zipalign, $apksigner, $d8, $androidJar)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required Android tool not found: $file" }
}

$work = Join-Path ([IO.Path]::GetDirectoryName($OutputApk)) ('.stock-oneui-root-candidate-' + [guid]::NewGuid().ToString('N'))
$decoded = Join-Path $work 'stock'
$unsigned = Join-Path $work 'candidate-unsigned.apk'
$aligned = Join-Path $work 'candidate-aligned.apk'
$javaClasses = Join-Path $work 'picker-classes'
$dexDir = Join-Path $work 'picker-dex'
New-Item -ItemType Directory -Path $work -Force | Out-Null
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JavaExe)
try {
    & $JavaExe -Xmx2g -jar $ApktoolJar d $StockOneUiApk -p $FrameworkDir --jobs 2 -o $decoded -f
    if ($LASTEXITCODE -ne 0) { throw 'Could not decode Samsung One UI Home.' }
    [xml] $manifest = Get-Content -LiteralPath (Join-Path $decoded 'AndroidManifest.xml') -Raw
    if ($manifest.manifest.package -ne 'com.sec.android.app.launcher') { throw 'Input is not the original Samsung One UI Home package.' }

    # The behavioral patcher was developed against the integrated clone, where
    # collision-prone references are shaded. Temporarily reproduce those
    # descriptors on Samsung's original classes, patch them, then restore every
    # original class/library name before assembling the Samsung package.
    $namespaceMap = @(
        @{ From = 'Landroidx/'; To = 'Lcom/nemotron/oneui/shaded/androidx/' },
        @{ From = 'Lkotlinx/coroutines/'; To = 'Lcom/nemotron/oneui/shaded/kotlinx/coroutines/' },
        @{ From = 'Lkotlin/'; To = 'Lcom/nemotron/oneui/shaded/kotlin/' },
        @{ From = 'Lcom/google/android/material/'; To = 'Lcom/nemotron/oneui/shaded/com/google/android/material/' },
        @{ From = 'Ly8/'; To = 'Lcom/nemotron/oneui/shaded/y8/' },
        @{ From = 'Lh8/'; To = 'Lcom/nemotron/oneui/shaded/h8/' }
    )
    $smaliDirs = @(Get-ChildItem -LiteralPath $decoded -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' })
    if ($smaliDirs.Count -lt 5) { throw 'Unexpected Samsung DEX layout.' }
    foreach ($file in Get-ChildItem -LiteralPath $decoded -Recurse -File -Filter '*.smali') {
        $text = [IO.File]::ReadAllText($file.FullName)
        $before = $text
        foreach ($map in $namespaceMap) { $text = $text.Replace($map.From, $map.To) }
        if ($text -cne $before) { [IO.File]::WriteAllText($file.FullName, $text, [Text.UTF8Encoding]::new($false)) }
    }

    $compatDir = Join-Path $decoded 'smali_classes6\com\nemotron\oneui\compat'
    New-Item -ItemType Directory -Path $compatDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/LauncherCompat-stock.smali') -Destination (Join-Path $compatDir 'LauncherCompat.smali') -Force
    & (Join-Path $PSScriptRoot 'patch-oneui-behavior.ps1') -DecodedRoot $decoded -StockHost

    # Return all temporary shaded references to Samsung's original library names.
    $reverseMap = @($namespaceMap | ForEach-Object { @{ From = $_.To; To = $_.From } })
    foreach ($file in Get-ChildItem -LiteralPath $decoded -Recurse -File -Filter '*.smali') {
        $text = [IO.File]::ReadAllText($file.FullName)
        $before = $text
        foreach ($map in $reverseMap) { $text = $text.Replace($map.From, $map.To) }
        if ($text -cne $before) { [IO.File]::WriteAllText($file.FullName, $text, [Text.UTF8Encoding]::new($false)) }
    }
    $remaining = Get-ChildItem -LiteralPath $decoded -Recurse -File -Filter '*.smali' |
        Select-String -SimpleMatch 'Lcom/nemotron/oneui/shaded/' | Select-Object -First 1
    if ($remaining) { throw "Temporary namespace remained in $($remaining.Path)" }

    & $JavaExe -Xmx2g -jar $ApktoolJar b $decoded --jobs 2 -o $unsigned
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $unsigned)) { throw 'Could not rebuild patched Samsung One UI Home.' }

    # The settings entry opens an in-place picker dialog. This helper is DEX-only
    # and adds no manifest component, provider, permission, or package metadata.
    New-Item -ItemType Directory -Path $javaClasses,$dexDir -Force | Out-Null
    & (Join-Path (Split-Path -Parent $JavaExe) 'javac.exe') -encoding UTF-8 --release 8 -Xlint:-options -cp $androidJar -d $javaClasses (Join-Path $PSScriptRoot 'oneui-compat/NemotronAppPicker.java')
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the app picker helper.' }
    & $d8 --min-api 29 --lib $androidJar --output $dexDir (Join-Path $javaClasses 'com\nemotron\oneui\compat\NemotronAppPicker.class')
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the app picker DEX.' }
    $dex = Join-Path $dexDir 'classes.dex'
    if (-not (Test-Path -LiteralPath $dex)) { throw 'D8 did not emit the app picker DEX.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
    try {
        if ($zip.GetEntry('classes7.dex')) { throw 'Unexpected classes7.dex collision in the Samsung APK.' }
        $entry = $zip.CreateEntry('classes7.dex', [IO.Compression.CompressionLevel]::NoCompression)
        $entryStream = $entry.Open()
        try { [IO.File]::OpenRead($dex).CopyTo($entryStream) } finally { $entryStream.Dispose() }
    } finally { $zip.Dispose() }

    & $zipalign -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Could not zipalign the stock-based candidate.' }
    & $apksigner sign --ks $DebugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk $aligned
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $OutputApk)) { throw 'Could not sign the session-only candidate.' }
    & $apksigner verify --verbose $OutputApk
    if ($LASTEXITCODE -ne 0) { throw 'Candidate signature verification failed.' }
    $hash = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
    [pscustomobject]@{
        OutputApk = $OutputApk
        Sha256 = $hash
        Package = 'com.sec.android.app.launcher'
        Includes = 'Nemotron Home policy, manual app pin dialog, social icon filtering, native shortcut return matching, package-event filter, and scheduler tuning.'
        Note = 'Runtime-only candidate. Never install; use only with the per-process root mount controller.'
    } | Format-List
} catch {
    Remove-Item -LiteralPath $OutputApk -Force -ErrorAction SilentlyContinue
    throw
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
