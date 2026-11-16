param(
    [Parameter(Mandatory = $true)] [string] $StockHoneyBoardApk,
    [Parameter(Mandatory = $true)] [string] $OutputApk,
    [Parameter(Mandatory = $true)] [string] $ApktoolJar,
    [Parameter(Mandatory = $true)] [string] $FrameworkDir,
    [Parameter(Mandatory = $true)] [string] $DebugKeystore,
    [string] $JavaExe = "$env:JAVA_HOME\bin\java.exe",
    [string] $AndroidSdk = "$env:ANDROID_HOME",
    [string] $ExpectedStockSha256 = '48bc8996a647e32b414bd78aab51b997908f600f1f19b900be6e9fd7b307edff'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
foreach ($file in @($StockHoneyBoardApk, $ApktoolJar, $FrameworkDir, $DebugKeystore, $JavaExe)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required input not found: $file" }
}
if (-not $AndroidSdk) { throw 'ANDROID_HOME must point to the Android SDK.' }
if (Test-Path -LiteralPath $OutputApk) { throw "Output already exists: $OutputApk" }
$stockHash = (Get-FileHash -LiteralPath $StockHoneyBoardApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ExpectedStockSha256 -and $stockHash -ne $ExpectedStockSha256.ToLowerInvariant()) {
    throw "Stock HoneyBoard hash does not match the approved A54 input: $stockHash"
}
$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools found under $AndroidSdk" }
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$d8 = Join-Path $buildTools.FullName 'd8.bat'
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$androidJar = Join-Path $AndroidSdk 'platforms\android-36\android.jar'
foreach ($file in @($zipalign, $apksigner, $d8, $aapt, $androidJar)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required Android tool not found: $file" }
}

$outputDir = Split-Path -Parent $OutputApk
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$work = Join-Path $outputDir ('.honeyboard-root-build-' + [guid]::NewGuid().ToString('N'))
$decoded = Join-Path $work 'stock'
$unsigned = Join-Path $work 'candidate-unsigned.apk'
$aligned = Join-Path $work 'candidate-aligned.apk'
$javaClasses = Join-Path $work 'classes'
$dexDir = Join-Path $work 'dex'
New-Item -ItemType Directory -Path $work -Force | Out-Null
try {
    & $JavaExe -Xmx1536m -jar $ApktoolJar d $StockHoneyBoardApk -p $FrameworkDir --jobs 2 -o $decoded -f
    if ($LASTEXITCODE -ne 0) { throw 'Could not decode the original Samsung Keyboard APK.' }
    [xml] $manifest = Get-Content -LiteralPath (Join-Path $decoded 'AndroidManifest.xml') -Raw
    if ($manifest.manifest.GetAttribute('package') -ne 'com.samsung.android.honeyboard') {
        throw 'Input is not the original Samsung HoneyBoard package.'
    }
    # HoneyBoard 5.9.30.88 already declares RECORD_AUDIO; the patch adds no app permission.
    $manifestText = [IO.File]::ReadAllText((Join-Path $decoded 'AndroidManifest.xml'))
    if (-not $manifestText.Contains('android.permission.RECORD_AUDIO')) { throw 'Stock HoneyBoard does not declare microphone permission.' }

    # Preserve the existing preference behavior: when Auto caps is off in HoneyBoard,
    # ignore an editor's CAP_SENTENCES hint. This leaves manual Shift untouched.
    & (Join-Path $PSScriptRoot 'patch-auto-caps.ps1') -DecodedApk $decoded

    $inputCallback = Join-Path $decoded 'smali_classes2\ii\a.smali'
    $serviceSmali = Join-Path $decoded 'smali_classes3\com\samsung\android\honeyboard\service\HoneyBoardService.smali'
    foreach ($file in @($inputCallback, $serviceSmali)) {
        if (-not (Test-Path -LiteralPath $file)) { throw "Expected HoneyBoard 5.9.30.88 class missing: $file" }
    }
    $inputText = [IO.File]::ReadAllText($inputCallback)
    if (-not $inputText.Contains('.class public Lii/a;') -or
        -not $inputText.Contains('.method public commitText(Ljava/lang/CharSequence;I)Z')) {
        throw 'HoneyBoard InputConnection callback no longer matches the audited version.'
    }
    $hook = @'
    invoke-static {p0, p1}, Lcom/samsung/android/honeyboard/nemotron/NemotronInlineDictation;->onSpaceCommit(Landroid/view/inputmethod/InputConnection;Ljava/lang/CharSequence;)Z
    move-result v3
    if-eqz v3, :nemotron_space_continue
    const/4 v4, 0x1
    return v4
    :nemotron_space_continue
'@
    $pattern = '(?s)(\.method public commitText\(Ljava/lang/CharSequence;I\)Z.*?iget v3, p0, Lii/a;->k:I\s+if-nez v3, :cond_2)'
    if ($inputText.Contains('NemotronInlineDictation;->onSpaceCommit')) {
        throw 'The stock callback unexpectedly already has a dictation hook.'
    }
    if ([regex]::Matches($inputText, $pattern).Count -ne 1) { throw 'Could not locate the unique HoneyBoard text-commit hook point.' }
    $inputText = [regex]::Replace($inputText, $pattern, '$1' + "`n" + $hook, 1)
    [IO.File]::WriteAllText($inputCallback, $inputText, [Text.UTF8Encoding]::new($false))

    $serviceText = [IO.File]::ReadAllText($serviceSmali)
    $servicePattern = '(?s)(\.method public onCreate\(\)V.*?invoke-super \{p0\}, Landroid/inputmethodservice/InputMethodService;->onCreate\(\)V)'
    if ([regex]::Matches($serviceText, $servicePattern).Count -ne 1) { throw 'Could not locate HoneyBoardService.onCreate super call.' }
    if ($serviceText.Contains('NemotronInlineDictation;->bindService')) { throw 'The stock IME already has a Nemotron service hook.' }
    $serviceText = [regex]::Replace($serviceText, $servicePattern,
        '$1' + "`n    invoke-static {p0}, Lcom/samsung/android/honeyboard/nemotron/NemotronInlineDictation;->bindService(Landroid/inputmethodservice/InputMethodService;)V", 1)
    [IO.File]::WriteAllText($serviceSmali, $serviceText, [Text.UTF8Encoding]::new($false))

    New-Item -ItemType Directory -Path $javaClasses,$dexDir -Force | Out-Null
    & (Join-Path (Split-Path -Parent $JavaExe) 'javac.exe') -encoding UTF-8 --release 8 -Xlint:-options `
        -cp $androidJar -d $javaClasses `
        (Join-Path $PSScriptRoot 'stock-host\NemotronInlineDictation.java') `
        (Join-Path $PSScriptRoot 'stock-host\NemotronKeyboardWaveform.java')
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the in-process voice dictation bridge.' }
    $classFiles = @(Get-ChildItem -LiteralPath $javaClasses -Recurse -Filter '*.class' | ForEach-Object FullName)
    & $d8 --min-api 29 --lib $androidJar --output $dexDir $classFiles
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the voice dictation DEX.' }
    $helperDex = Join-Path $dexDir 'classes.dex'
    if (-not (Test-Path -LiteralPath $helperDex)) { throw 'D8 did not emit the dictation DEX.' }

    & $JavaExe -Xmx1536m -jar $ApktoolJar b $decoded --jobs 2 -o $unsigned
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $unsigned)) { throw 'Could not rebuild Samsung Keyboard.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
    try {
        if ($zip.GetEntry('classes5.dex')) { throw 'Unexpected classes5.dex collision in stock APK.' }
        $entry = $zip.CreateEntry('classes5.dex', [IO.Compression.CompressionLevel]::NoCompression)
        $entryStream = $entry.Open()
        $sourceStream = [IO.File]::OpenRead($helperDex)
        try { $sourceStream.CopyTo($entryStream) } finally { $sourceStream.Dispose(); $entryStream.Dispose() }
    } finally { $zip.Dispose() }

    & $zipalign -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Could not align the candidate APK.' }
    & $apksigner sign --ks $DebugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Could not sign the session-only candidate.' }
    & $apksigner verify --verbose $OutputApk
    if ($LASTEXITCODE -ne 0) { throw 'Candidate signature verification failed.' }
    $badging = @(& $aapt dump badging $OutputApk)
    if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='com\.samsung\.android\.honeyboard'") {
        throw 'The candidate package ID is not Samsung HoneyBoard.'
    }
    [pscustomobject]@{
        OutputApk = $OutputApk
        OriginalSha256 = $stockHash
        CandidateSha256 = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
        Package = 'com.samsung.android.honeyboard'
        Version = '5.9.30.88'
        Includes = 'Native HoneyBoard retained; double-space Spanish dictation with spacebar waveform and lowercase output; Auto caps preference honored; dictation toasts removed.'
        Runtime = 'Root namespace mount only. Does not install, replace, or write to /system.'
    } | Format-List
} catch {
    Remove-Item -LiteralPath $OutputApk -Force -ErrorAction SilentlyContinue
    throw
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
