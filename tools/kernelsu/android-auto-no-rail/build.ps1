param(
    [string]$AndroidSdk = 'C:/Users/adaredu/develop/android-sdk',
    [string]$Java = 'C:/Users/adaredu/develop/jdk-21-runtime/bin/java.exe',
    [string]$OutputDirectory = "$env:TEMP/nemotron-android-auto-no-rail-build"
)
$ErrorActionPreference = 'Stop'
throw 'RETIRADO: this module caused a SystemServer boot failure on the Galaxy A54. See docs/kernelsu-recovery-2026-11-06.md; do not build or install it.'
$out = [IO.Path]::GetFullPath($OutputDirectory)
$bt = Join-Path $AndroidSdk 'build-tools/36.0.0'
$androidJar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
New-Item -ItemType Directory -Force $out | Out-Null
$moduleRoot = Join-Path $out 'module'
if (Test-Path -LiteralPath $moduleRoot) {
    $tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $out.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace generated module outside TEMP: $moduleRoot"
    }
    Remove-Item -LiteralPath $moduleRoot -Recurse -Force
}
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}
Invoke-Checked "$bt/aapt2.exe" @('compile', '--dir', "$PSScriptRoot/overlay/res", '-o', "$out/resources.zip")
Invoke-Checked "$bt/aapt2.exe" @('link', '-o', "$out/unsigned.apk", '--manifest', "$PSScriptRoot/overlay/AndroidManifest.xml", '-I', $androidJar, "$out/resources.zip")
Invoke-Checked "$bt/zipalign.exe" @('-f', '4', "$out/unsigned.apk", "$out/aligned.apk")
$destination = Join-Path $out 'module/system/product/overlay'
New-Item -ItemType Directory -Force $destination | Out-Null
$overlayConfig = Join-Path $destination 'config'
New-Item -ItemType Directory -Force $overlayConfig | Out-Null
Copy-Item "$PSScriptRoot/overlay-config/config.xml" "$overlayConfig/config.xml" -Force
$keystore = Join-Path $env:USERPROFILE '.android/debug.keystore'
if (-not (Test-Path -LiteralPath $keystore)) { throw "Debug keystore not found: $keystore" }
Invoke-Checked $Java @('-jar', "$bt/lib/apksigner.jar", 'sign', '--ks', $keystore, '--ks-pass', 'pass:android', '--out', "$destination/NemotronAndroidAutoNoRail.apk", "$out/aligned.apk")
Invoke-Checked $Java @('-jar', "$bt/lib/apksigner.jar", 'verify', "$destination/NemotronAndroidAutoNoRail.apk")
Remove-Item -LiteralPath "$destination/NemotronAndroidAutoNoRail.apk.idsig" -Force -ErrorAction SilentlyContinue
Copy-Item "$PSScriptRoot/module.prop" "$moduleRoot/module.prop" -Force
Compress-Archive -Path "$out/module/*" -DestinationPath "$out/NemotronAndroidAutoNoRail.zip" -Force
Write-Output "$out/NemotronAndroidAutoNoRail.zip"
