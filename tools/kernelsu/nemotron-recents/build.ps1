param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$JdkHome = 'C:/Users/adaredu/develop/jdk-21-runtime',
    [Parameter(Mandatory=$true)][string]$HomeApk,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$out = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force $out | Out-Null
$bt = Join-Path $AndroidSdk 'build-tools/36.0.0'
if (-not (Test-Path -LiteralPath $HomeApk)) { throw 'Provide the APK pulled from the current Nemotron Home installation.' }
$package = & "$bt/aapt2.exe" dump badging $HomeApk
if ($LASTEXITCODE -ne 0 -or -not ($package -match "package: name='com.nemotron.voiceime2'")) { throw 'Unexpected Home APK package.' }
$env:JAVA_HOME = $JdkHome
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}
Invoke-Checked "$bt/aapt2.exe" @('compile', '--dir', "$PSScriptRoot/overlay/res", '-o', "$out/resources.zip")
Invoke-Checked "$bt/aapt2.exe" @('link', '-o', "$out/unsigned.apk", '--manifest', "$PSScriptRoot/overlay/AndroidManifest.xml", '-I', "$AndroidSdk/platforms/android-36/android.jar", "$out/resources.zip")
Invoke-Checked "$bt/zipalign.exe" @('-f', '4', "$out/unsigned.apk", "$out/aligned.apk")
$destination = Join-Path $out 'module/system/product/overlay/NemotronRecents'
New-Item -ItemType Directory -Force $destination | Out-Null
Invoke-Checked "$bt/apksigner.bat" @('sign', '--ks', "$env:USERPROFILE/.android/debug.keystore", '--ks-pass', 'pass:android', '--out', "$destination/NemotronRecents.apk", "$out/aligned.apk")
Invoke-Checked "$bt/apksigner.bat" @('verify', "$destination/NemotronRecents.apk")
Copy-Item "$PSScriptRoot/module.prop" "$out/module/module.prop" -Force
$systemHome = Join-Path $out 'module/system/app/NemotronHome'
New-Item -ItemType Directory -Force $systemHome | Out-Null
Copy-Item -LiteralPath $HomeApk -Destination "$systemHome/NemotronHome.apk" -Force
# The exact installed APK becomes a system package through the module mount.
# Do not uninstall the data package or alter its UID/data.
Compress-Archive -Path "$out/module/*" -DestinationPath "$out/NemotronRecents-experimental.zip" -Force
Write-Output "$out/NemotronRecents-experimental.zip"
