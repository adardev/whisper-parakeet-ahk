param(
    [Parameter(Mandatory=$true)][string]$StandaloneCoreApk,
    [Parameter(Mandatory=$true)][string]$KeyboardApk,
    [Parameter(Mandatory=$true)][string]$UiApk,
    [Parameter(Mandatory=$true)][string]$KeyboardReferenceApk,
    [Parameter(Mandatory=$true)][string]$HomeReferenceApk,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$ApktoolJar="$env:USERPROFILE\NemotronBuild\apktool_2.12.1.jar",
    [string]$AndroidSdk="$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaExe="$env:LOCALAPPDATA\NemotronToolchain\jdk17\jdk-17.0.20.1+1\bin\java.exe"
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -Path (Join-Path $PSScriptRoot 'PruneNemotronDex.cs')
$output=[IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Choose a new, empty output directory.' }
$drive=[IO.Path]::GetPathRoot($output).TrimEnd('\').TrimEnd(':')
if ((Get-PSDrive $drive).Free -lt 3GB) { throw 'Need 3 GB free for the bounded component rebuild.' }
foreach ($path in @($StandaloneCoreApk,$KeyboardApk,$UiApk,$KeyboardReferenceApk,$HomeReferenceApk,$ApktoolJar,$JavaExe)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing input: $path" }
}
$env:JAVA_HOME=Split-Path (Split-Path $JavaExe -Parent) -Parent
$bt=(Get-ChildItem (Join-Path $AndroidSdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$work=Join-Path $output '.generated'
New-Item -ItemType Directory -Path $work -Force | Out-Null
function Invoke-Tool([string[]]$Arguments) {
    & $JavaExe -Xmx448m -XX:ReservedCodeCacheSize=64m -jar $ApktoolJar @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'APKTool failed; originals were not modified.' }
}
function Assert-Model([string]$Apk,[bool]$Expected) {
    $zip=[IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        $entry=$zip.GetEntry('assets/whisper/ggml-tiny.bin')
        if (($null -ne $entry) -ne $Expected) { throw "Whisper model ownership failed: $Apk" }
        if ($Expected -and $entry.Length -ne 77691713L) { throw 'Whisper model length changed.' }
    } finally { $zip.Dispose() }
}
function Get-Certificate([string]$Apk) {
    $cert=@(& "$bt\apksigner.bat" verify --print-certs $Apk)
    if ($LASTEXITCODE -ne 0) { throw "Invalid signature: $Apk" }
    return ($cert | Where-Object { $_ -match 'SHA-256 digest:' } | Select-Object -First 1)
}
$certificate=Get-Certificate $StandaloneCoreApk
foreach ($apk in @($KeyboardApk,$UiApk)) {
    if ((Get-Certificate $apk) -ne $certificate) { throw 'Signer mismatch; refusing to generate an incompatible update.' }
}
Assert-Model $StandaloneCoreApk $false
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out (Join-Path $output 'Nemotron-Core.apk') $StandaloneCoreApk
if ($LASTEXITCODE -ne 0) { throw 'Core signing failed.' }
$coreCode=Join-Path $work 'core-code'
Invoke-Tool @('d',$StandaloneCoreApk,'-r','-j','1','-o',$coreCode)
foreach ($component in @('keyboard','ui')) {
    $inputApk=if ($component -eq 'keyboard') {$KeyboardApk} else {$UiApk}
    $tree=Join-Path $work $component
    Invoke-Tool @('d',$inputApk,'-r','-j','1','-o',$tree)
    if ($component -eq 'keyboard') {
        $record=Get-ChildItem $tree -Recurse -File -Filter 'VoiceRecordService.smali' | Select-Object -First 1
        if (-not $record) { throw 'Keyboard voice service missing.' }
        $destination=Split-Path $record.FullName -Parent
        $helpers=@(Get-ChildItem $coreCode -Recurse -File -Filter 'KeyboardMemoryController*.smali')
        if ($helpers.Count -eq 0) { throw 'Rebuild Core with KeyboardMemoryController first.' }
        foreach ($helper in $helpers) { Copy-Item -LiteralPath $helper.FullName -Destination (Join-Path $destination $helper.Name) -Force }
        & (Join-Path $PSScriptRoot 'patch-keyboard-memory.ps1') -DecodedRoot $tree
    } else {
        & (Join-Path $PSScriptRoot 'trim-nemotron-ui-assets.ps1') -DecodedRoot $tree -KeyboardReferenceApk $KeyboardReferenceApk -HomeReferenceApk $HomeReferenceApk
    }
    $manifest=@(& "$bt\aapt.exe" dump xmltree $inputApk AndroidManifest.xml)
    [PruneNemotronDex]::Run($tree,$component,($manifest -join "`n"))
    Copy-Item -LiteralPath (Join-Path $tree 'pruned-classes.txt') -Destination (Join-Path $output "$component-pruned-classes.txt")
    $unsigned=Join-Path $work "$component-unsigned.apk"
    $aligned=Join-Path $work "$component-aligned.apk"
    Invoke-Tool @('b',$tree,'-j','1','-o',$unsigned)
    & "$bt\zipalign.exe" -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
    $label=if ($component -eq 'keyboard') {'Keyboard'} else {'UI'}
    $final=Join-Path $output "Nemotron-$label.apk"
    & "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out $final $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    if ((Get-Certificate $final) -ne $certificate) { throw 'Output signature mismatch.' }
    Assert-Model $final ($component -eq 'keyboard')
}
# Generated paths are confined to the fresh output directory; inputs/data backups
# are never removed. Retain them separately until device verification completes.
if ([IO.Path]::GetFullPath($work) -ne (Join-Path $output '.generated')) { throw 'Unexpected staging root.' }
Remove-Item -LiteralPath $work -Recurse -Force
Write-Output "Three component APKs ready in $output"
