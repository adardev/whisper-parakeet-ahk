param(
    [string] $OutputDirectory = "$env:USERPROFILE\NemotronBuild\SplitOutput",
    [string] $HoneyBoardApk = "$env:USERPROFILE\NemotronBuild\HoneyBoard-5.9.30.88.apk",
    [string] $UiInputApk = "$env:USERPROFILE\NemotronBuild\Nemotron-Manual-Apps-v15.apk",
    [string] $ApktoolJar = "$env:USERPROFILE\NemotronBuild\apktool_2.12.1.jar",
    [string] $AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string] $JavaExe = "$env:LOCALAPPDATA\NemotronToolchain\jdk17\jdk-17.0.20.1+1\bin\java.exe",
    [string] $FrameworkDir = "$env:LOCALAPPDATA\apktool\framework"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = Split-Path -Parent $PSScriptRoot
$outputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$driveName = [IO.Path]::GetPathRoot($outputDirectory).TrimEnd('\').TrimEnd(':')
$coreInputApk = Join-Path $outputDirectory 'Nemotron-Core-Integrated.apk'
$uiRollbackApk = Join-Path $outputDirectory 'Nemotron-UI-Rollback.apk'
$uiOutputApk = Join-Path $outputDirectory 'Nemotron-UI\Nemotron-UI.apk'
$coreOutputApk = Join-Path $outputDirectory 'Nemotron\Nemotron-Core.apk'
$keyboardOutputApk = Join-Path $outputDirectory 'Nemotron-Keyboard\Nemotron-Keyboard.apk'
$debugKeystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
$required = @($HoneyBoardApk, $UiInputApk, $ApktoolJar, $JavaExe, $debugKeystore)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file not found: $path" }
}
if (-not (Test-Path -LiteralPath $AndroidSdk -PathType Container)) { throw "Android SDK not found: $AndroidSdk" }
if (-not (Test-Path -LiteralPath $FrameworkDir -PathType Container)) { throw "APKTool framework not found: $FrameworkDir" }
if ((Get-PSDrive -Name $driveName).Free -lt 2GB) {
    throw "Need at least 2 GB free on $driveName before starting. No build started."
}
if (Test-Path -LiteralPath $outputDirectory -PathType Container) {
    $existing = Get-ChildItem -LiteralPath $outputDirectory -Force |
        Where-Object { $_.Extension -ne '.png' } | Select-Object -First 1
    if ($existing) { throw "Output folder may contain PNG evidence only; move or remove generated build files first: $outputDirectory" }
} else {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$javaHome = Split-Path (Split-Path $JavaExe -Parent) -Parent
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe') -PathType Leaf)) {
    throw "Java executable is not under a JDK bin directory: $JavaExe"
}
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
$env:PATH = (Join-Path $javaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH

Push-Location $repo
try {
    & (Join-Path $repo 'gradlew.bat') ':apps:nemotron:assembleDebug' '--offline' '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw 'Nemotron app build failed.' }
    $appApk = Join-Path $repo 'apps\nemotron\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $appApk -PathType Leaf)) { throw "Gradle APK not found: $appApk" }

    # Rollback must restore the complete existing launcher package and its
    # keyboard providers. The app module build is Nemotron Core, not Home UI.
    Copy-Item -LiteralPath $UiInputApk -Destination $uiRollbackApk

    & (Join-Path $repo 'apps\nemotron-keyboard\build\merge-honeyboard.ps1') `
        -HoneyBoardApk $HoneyBoardApk `
        -NemotronApk $appApk `
        -OutputApk $coreInputApk `
        -ApktoolJar $ApktoolJar `
        -AndroidSdk $AndroidSdk `
        -JavaExe $JavaExe
    if ($LASTEXITCODE -ne 0) { throw 'HoneyBoard integration failed.' }

    & (Join-Path $PSScriptRoot 'split-nemotron-packages.ps1') `
        -CoreInputApk $coreInputApk `
        -UiInputApk $UiInputApk `
        -OutputDirectory $outputDirectory `
        -ApktoolJar $ApktoolJar `
        -AndroidSdk $AndroidSdk `
        -JavaExe $JavaExe `
        -FrameworkDir $FrameworkDir `
        -DebugKeystore $debugKeystore
    if ($LASTEXITCODE -ne 0) { throw 'Nemotron three-package split failed.' }

    Write-Host "Core: $coreOutputApk"
    Write-Host "Keyboard: $keyboardOutputApk"
    Write-Host "UI:   $uiOutputApk"
    Write-Host "Core integration input retained: $coreInputApk"
    Write-Host "Full UI rollback APK retained: $uiRollbackApk"
} finally {
    Pop-Location
}
