param(
    [Parameter(Mandatory=$true)][string]$DecodedRoot,
    [Parameter(Mandatory=$true)][string]$ApktoolJar,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JdkHome = "$env:JAVA_HOME"
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path "$JdkHome/bin/javac.exe")) {
    $candidates = @('C:/Program Files/Apache NetBeans/jdk')
    $candidates += @(Get-ChildItem 'C:/Program Files/JetBrains' -Directory -ErrorAction SilentlyContinue | ForEach-Object { Join-Path $_.FullName 'jbr' })
    $JdkHome = $candidates | Where-Object { Test-Path "$_/bin/javac.exe" } | Select-Object -First 1
}
if (-not $JdkHome) { throw 'A JDK with javac is required for the One UI compatibility code.' }
$work = Join-Path (Split-Path -Parent $DecodedRoot) 'java-compat'
New-Item -ItemType Directory -Force "$work/classes", "$work/dex", "$work/host" | Out-Null
$staleClasses = Get-ChildItem "$work/classes" -Recurse -File -Filter 'GestureReturnController*.class' -ErrorAction SilentlyContinue
if ($staleClasses) { $staleClasses | Remove-Item -Force }
$jar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
$sources = (Get-ChildItem (Join-Path $PSScriptRoot 'oneui-compat/java') -Recurse -Filter '*.java').FullName
& "$JdkHome/bin/javac.exe" --release 8 -Xlint:-options -cp $jar -d "$work/classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'One UI compatibility Java compilation failed' }
# Framework stubs are compile-only; only our package enters the APK.
$classes = (Get-ChildItem "$work/classes/com" -Recurse -Filter '*.class').FullName
$buildTools = Get-ChildItem (Join-Path $AndroidSdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
& "$JdkHome/bin/java.exe" -cp (Join-Path $buildTools.FullName 'lib/d8.jar') com.android.tools.r8.D8 --min-api 29 --lib $jar --output "$work/dex" $classes
if ($LASTEXITCODE -ne 0) { throw 'One UI compatibility DEX compilation failed' }
& "$JdkHome/bin/javac.exe" --release 8 -Xlint:-options -cp $ApktoolJar -d "$work/host" (Join-Path $PSScriptRoot 'oneui-compat/build/DisassembleCompat.java')
if ($LASTEXITCODE -ne 0) { throw 'Compatibility disassembler compilation failed' }
$destination = Join-Path $DecodedRoot 'smali_classes23'
$staleSmali = Get-ChildItem $destination -Recurse -File -Filter 'GestureReturnController*.smali' -ErrorAction SilentlyContinue
if ($staleSmali) { $staleSmali | Remove-Item -Force }
& "$JdkHome/bin/java.exe" -cp "$work/host;$ApktoolJar" DisassembleCompat "$work/dex/classes.dex" $destination
if ($LASTEXITCODE -ne 0) { throw 'Gesture compatibility disassembly failed' }
Write-Host 'One UI compatibility helpers compiled; the simulated icon-return overlay is excluded.'
