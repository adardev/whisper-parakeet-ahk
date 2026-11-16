param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$OutputDirectory = "$env:TEMP/oneui-quickstep-build",
    [string]$JdkHome = 'C:/Users/adaredu/develop/temurin17-task/jdk-17.0.21+9'
)
$ErrorActionPreference = 'Stop'
$out = [IO.Path]::GetFullPath($OutputDirectory)
$bt = Join-Path $AndroidSdk 'build-tools/36.0.0'
if (-not (Test-Path -LiteralPath "$bt/aapt2.exe")) { throw 'Android SDK Build Tools 36.0.0 not found.' }
if (-not (Test-Path -LiteralPath "$bt/apksigner.bat")) { throw 'apksigner not found.' }
$env:JAVA_HOME = $JdkHome
New-Item -ItemType Directory -Force $out | Out-Null

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}

Invoke-Checked "$bt/aapt2.exe" @('compile', '--dir', "$PSScriptRoot/overlay/res", '-o', "$out/resources.zip")
Invoke-Checked "$bt/aapt2.exe" @('link', '-o', "$out/unsigned.apk", '--manifest', "$PSScriptRoot/overlay/AndroidManifest.xml", '-I', "$AndroidSdk/platforms/android-36/android.jar", "$out/resources.zip")
Invoke-Checked "$bt/zipalign.exe" @('-f', '4', "$out/unsigned.apk", "$out/aligned.apk")
$module = Join-Path $out 'module'
$overlay = Join-Path $module 'system/product/overlay/OneUIQuickstep'
New-Item -ItemType Directory -Force $overlay | Out-Null
Invoke-Checked "$bt/apksigner.bat" @('sign', '--ks', "$env:USERPROFILE/.android/debug.keystore", '--ks-pass', 'pass:android', '--out', "$overlay/OneUIQuickstep.apk", "$out/aligned.apk")
Invoke-Checked "$bt/apksigner.bat" @('verify', "$overlay/OneUIQuickstep.apk")
Copy-Item "$PSScriptRoot/module.prop" "$module/module.prop" -Force
$zip = Join-Path $out 'OneUIQuickstep-KernelSU.zip'
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path "$module/*" -DestinationPath $zip -Force
Write-Output $zip
