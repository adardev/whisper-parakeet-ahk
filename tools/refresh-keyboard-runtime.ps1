param(
    [Parameter(Mandatory=$true)][string]$CoreApk,
    [Parameter(Mandatory=$true)][string]$KeyboardApk,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [Parameter(Mandatory=$true)][string]$ApktoolJar,
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$Keystore="$env:USERPROFILE\.android\debug.keystore"
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($p in @($CoreApk,$KeyboardApk,$ApktoolJar,$JavaExe,$Keystore)) {
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { throw "Missing input: $p" }
}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Choose a fresh output directory.' }
New-Item -ItemType Directory -Path $output | Out-Null
$env:JAVA_HOME=Split-Path (Split-Path $JavaExe -Parent) -Parent
$bt=Join-Path $AndroidSdk 'build-tools\36.0.0'
$core=Join-Path $output 'core-code'
$keyboard=Join-Path $output 'keyboard-code'
function Invoke-Apktool([string[]]$Arguments) {
    & $JavaExe -Xmx768m -jar $ApktoolJar @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'APKTool failed; no APK will be installed.' }
}
function Signer([string]$Apk) {
    $lines=@(& "$bt\apksigner.bat" verify --print-certs $Apk)
    if ($LASTEXITCODE -ne 0) { throw "Invalid APK signature: $Apk" }
    $line=$lines | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' } | Select-Object -First 1
    if (-not $line) { throw 'Missing signer digest.' }
    return $line
}
if ((Signer $CoreApk) -ne (Signer $KeyboardApk)) { throw 'Core and Keyboard signing certificates differ.' }
$badging=@(& "$bt\aapt.exe" dump badging $KeyboardApk)
if ($LASTEXITCODE -ne 0 -or -not ($badging[0] -match "name='com.nemotron.keyboard'")) { throw 'Wrong Keyboard package.' }
Invoke-Apktool @('d',$CoreApk,'-r','-j','1','-o',$core)
Invoke-Apktool @('d',$KeyboardApk,'-r','-j','1','-o',$keyboard)
foreach($family in @('SecureStore','KeyboardDictationFeedback')) {
    $destinations=@(Get-ChildItem -LiteralPath $keyboard -Recurse -File -Filter "$family.smali")
    $sources=@(Get-ChildItem -LiteralPath $core -Recurse -File -Filter "$family*.smali" |
        Where-Object { $_.BaseName -eq $family -or $_.BaseName.StartsWith($family+'$') })
    if ($destinations.Count -ne 1 -or $sources.Count -eq 0) { throw "Unexpected class family layout: $family" }
    $destination=Split-Path $destinations[0].FullName -Parent
    foreach($source in $sources) { Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $destination $source.Name) }
}
& (Join-Path $PSScriptRoot 'patch-keyboard-callback-cleanup.ps1') -DecodedRoot $keyboard
$unsigned=Join-Path $output 'keyboard-unsigned.apk'
$aligned=Join-Path $output 'keyboard-aligned.apk'
$final=Join-Path $output 'Nemotron-Keyboard-candidate.apk'
Invoke-Apktool @('b',$keyboard,'-j','1','-o',$unsigned)
& "$bt\zipalign.exe" -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
& "$bt\apksigner.bat" sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $final $aligned
if ($LASTEXITCODE -ne 0 -or (Signer $final) -ne (Signer $KeyboardApk)) { throw 'Output signer verification failed.' }

# Prove every non-code entry is byte-identical to the base: permissions,
# components, resources, native libraries, models and vendor assets stay intact.
function Entry-Hash($Entry) {
    $stream=$Entry.Open()
    $hash=[Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($hash.ComputeHash($stream)) }
    finally { $stream.Dispose(); $hash.Dispose() }
}
$before=[IO.Compression.ZipFile]::OpenRead($KeyboardApk)
$after=[IO.Compression.ZipFile]::OpenRead($final)
try {
    $names=@{}
    foreach($entry in $before.Entries) {
        if ($entry.FullName -match '^classes\d*\.dex$|^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$|/$') { continue }
        $names[$entry.FullName]=$true
        $replacement=$after.GetEntry($entry.FullName)
        if (-not $replacement -or (Entry-Hash $entry) -ne (Entry-Hash $replacement)) {
            throw "Non-code entry changed: $($entry.FullName)"
        }
    }
    foreach($entry in $after.Entries) {
        if ($entry.FullName -match '^classes\d*\.dex$|^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$|/$') { continue }
        if (-not $names.ContainsKey($entry.FullName)) { throw "Unexpected new entry: $($entry.FullName)" }
    }
    $manifest="Preserved non-code entries: $($names.Count)`nCore: $((Get-FileHash $CoreApk -Algorithm SHA256).Hash)`nBase Keyboard: $((Get-FileHash $KeyboardApk -Algorithm SHA256).Hash)`nCandidate: $((Get-FileHash $final -Algorithm SHA256).Hash)`n"
    $manifest | Set-Content -Encoding utf8 (Join-Path $output 'verification.txt')
} finally { $before.Dispose(); $after.Dispose() }
Write-Output "Verified candidate (not installed): $final"
