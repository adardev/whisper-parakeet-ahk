param(
    [Parameter(Mandatory=$true)][string]$DecodedRoot,
    [Parameter(Mandatory=$true)][string]$BaseApk,
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [Parameter(Mandatory=$true)][string]$ApktoolJar,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JdkHome = 'C:/Program Files/Apache NetBeans/jdk'
)
$ErrorActionPreference='Stop'
$work=Join-Path (Split-Path -Parent $DecodedRoot) 'compat-update'
New-Item -ItemType Directory -Force "$work/host" | Out-Null
& "$JdkHome/bin/javac.exe" --release 8 -Xlint:-options -cp $ApktoolJar -d "$work/host" (Join-Path $PSScriptRoot 'oneui-compat/build/AssembleCompat.java')
if ($LASTEXITCODE -ne 0) { throw 'Assembler compilation failed' }
& "$JdkHome/bin/java.exe" -cp "$work/host;$ApktoolJar" AssembleCompat (Join-Path $DecodedRoot 'smali_classes23') "$work/classes23.dex"
if ($LASTEXITCODE -ne 0) { throw 'Compatibility DEX assembly failed' }
$dexBytes=[IO.File]::ReadAllBytes((Join-Path $work 'classes23.dex'))
if ([Text.Encoding]::ASCII.GetString($dexBytes,0,7) -ne "dex`n039" -or [BitConverter]::ToInt32($dexBytes,36) -ne 112) { throw 'Unexpected standalone DEX header' }
if (-not [Text.Encoding]::UTF8.GetString($dexBytes).Contains('Lcom/nemotron/oneui/compat/LauncherCompat;')) { throw 'Required launcher compatibility class missing' }
$unsigned=Join-Path $work 'unsigned.apk'
Copy-Item -LiteralPath $BaseApk -Destination $unsigned -Force
$zip=[IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entry=$zip.GetEntry('classes23.dex')
    if (-not $entry) { throw 'The base APK is not the expected merged build (classes23.dex missing).' }
    $stream=$entry.Open()
    try {
        $stream.SetLength(0)
        $source=[IO.File]::OpenRead((Join-Path $work 'classes23.dex'))
        try { $source.CopyTo($stream) } finally { $source.Dispose() }
    } finally { $stream.Dispose() }
} finally { $zip.Dispose() }
$bt=Get-ChildItem (Join-Path $AndroidSdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
& (Join-Path $bt.FullName 'zipalign.exe') -f -p 4 $unsigned "$work/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
$env:JAVA_HOME=$JdkHome
& (Join-Path $bt.FullName 'apksigner.bat') sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk "$work/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Signing failed' }
& (Join-Path $bt.FullName 'apksigner.bat') verify $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed' }
