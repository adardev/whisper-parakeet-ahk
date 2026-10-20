param(
    [Parameter(Mandatory = $true)] [string] $CoreInputApk,
    [Parameter(Mandatory = $true)] [string] $UiInputApk,
    [Parameter(Mandatory = $true)] [string] $OutputDirectory,
    [Parameter(Mandatory = $true)] [string] $ApktoolJar,
    [Parameter(Mandatory = $true)] [string] $AndroidSdk,
    [Parameter(Mandatory = $true)] [string] $JavaExe,
    [Parameter(Mandatory = $true)] [string] $FrameworkDir,
    [string] $DebugKeystore = "$env:USERPROFILE\.android\debug.keystore"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$corePackage = 'com.nemotron.core'
$keyboardPackage = 'com.nemotron.keyboard'
$uiPackage = 'com.nemotron.voiceime2'
$oldPackage = 'com.nemotron.voiceime2'
$androidNs = 'http://schemas.android.com/apk/res/android'
$androidToolsNs = 'http://schemas.android.com/tools'
$shortcutComponents = @(
    'com.nemotron.voiceime.ui.ShortcutActivity',
    'com.nemotron.voiceime.ui.ShortcutPickerActivity'
)

foreach ($file in @($CoreInputApk, $UiInputApk, $ApktoolJar, $JavaExe, $DebugKeystore)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Required file not found: $file" }
}
if (-not (Test-Path -LiteralPath $FrameworkDir -PathType Container)) { throw "Framework directory not found: $FrameworkDir" }
if (-not (Test-Path -LiteralPath $AndroidSdk -PathType Container)) { throw "Android SDK not found: $AndroidSdk" }

$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools under $AndroidSdk" }
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
foreach ($file in @($aapt, $zipalign, $apksigner)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Build tool not found: $file" }
}

function Get-SignerDigest([string] $ApkPath) {
    $output = @(& $apksigner verify --print-certs $ApkPath 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Could not verify APK signer: $ApkPath`n$($output -join "`n")" }
    $line = $output | Where-Object { $_ -match 'Signer #1 certificate SHA-256 digest:' } | Select-Object -First 1
    if (-not $line -or $line -notmatch '([0-9a-fA-F]{64})\s*$') {
        throw "Could not read SHA-256 signer digest from $ApkPath"
    }
    return $Matches[1].ToLowerInvariant()
}

$CoreInputApk = (Resolve-Path -LiteralPath $CoreInputApk).Path
$UiInputApk = (Resolve-Path -LiteralPath $UiInputApk).Path
$javaHome = Split-Path (Split-Path $JavaExe -Parent) -Parent
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe') -PathType Leaf)) {
    throw "Java executable is not under a JDK bin directory: $JavaExe"
}
# Android build-tools scripts (including apksigner.bat) consult JAVA_HOME even
# when the Java executable is invoked by an absolute path. Ignore stale global
# JAVA_HOME values and use the JDK explicitly passed to this build.
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
$uiInputSigner = Get-SignerDigest $UiInputApk
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
}
$coreOutput = Join-Path $OutputDirectory 'Nemotron-Core.apk'
$keyboardOutput = Join-Path $OutputDirectory 'Nemotron-Keyboard.apk'
$uiOutput = Join-Path $OutputDirectory 'Nemotron-UI.apk'
foreach ($file in @($coreOutput, $keyboardOutput, $uiOutput)) {
    if (Test-Path -LiteralPath $file) { throw "Output already exists; move it first: $file" }
}

$drive = [IO.Path]::GetPathRoot($OutputDirectory)
$freeBytes = (Get-PSDrive -Name $drive.TrimEnd('\').TrimEnd(':')).Free
if ($freeBytes -lt 2GB) {
    throw "Need at least 2 GB free on $drive for staged APKtool rebuilds; found $([math]::Round($freeBytes / 1GB, 2)) GB. No build started."
}

$work = Join-Path $OutputDirectory ('.nemotron-split-' + [guid]::NewGuid().ToString('N'))
$coreTree = Join-Path $work 'core'
$keyboardTree = Join-Path $work 'keyboard'
$uiTree = Join-Path $work 'ui'
New-Item -ItemType Directory -Path $work | Out-Null

function Invoke-Apktool([string[]] $Arguments, [string] $Failure) {
    & $JavaExe -Xmx2g -jar $ApktoolJar @Arguments
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

function Get-ComponentName([System.Xml.XmlNode] $Component, [string] $PackageName) {
    $name = $Component.GetAttribute('name', $androidNs)
    if (-not $name) { return '' }
    if ($name.StartsWith('.')) { return $PackageName + $name }
    if (-not $name.Contains('.')) { return $PackageName + '.' + $name }
    return $name
}

function Set-ApplicationId([string] $DecodedRoot, [string] $NewPackage) {
    $manifestPath = Join-Path $DecodedRoot 'AndroidManifest.xml'
    [xml] $manifest = Get-Content -LiteralPath $manifestPath -Raw
    $manifest.manifest.SetAttribute('package', $NewPackage)
    $manifest.Save($manifestPath)
}

function Rewrite-PackageReferences([string] $DecodedRoot, [string] $NewPackage) {
    # Rewrite exact textual package references everywhere they occur in DEX
    # smali and compiled resource XML. Authorities and local permissions keep
    # their original suffixes, so unrelated providers can never collapse to
    # generated aliases such as .p7 or .perm16.
    $extensions = @('.smali', '.xml', '.json', '.properties', '.txt', '.yml', '.yaml')
    foreach ($file in Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File) {
        if ($extensions -notcontains $file.Extension.ToLowerInvariant()) { continue }
        $text = [IO.File]::ReadAllText($file.FullName)
        if ($text.Contains($oldPackage)) {
            [IO.File]::WriteAllText(
                $file.FullName,
                $text.Replace($oldPackage, $NewPackage),
                [Text.UTF8Encoding]::new($false))
        }
    }
    Set-ApplicationId $DecodedRoot $NewPackage

    # AndroidX Startup's host and shaded HoneyBoard copy both declare the
    # same default authority. Give the shaded, in-process initializer its own
    # authority; its metadata and class remain intact.
    $manifestPath = Join-Path $DecodedRoot 'AndroidManifest.xml'
    [xml] $manifest = Get-Content -LiteralPath $manifestPath -Raw
    foreach ($provider in $manifest.SelectNodes('//provider')) {
        $name = $provider.GetAttribute('name', $androidNs)
        $authority = $provider.GetAttribute('authorities', $androidNs)
        if ($name -eq 'com.nemotron.honeyboard.shaded.androidx.startup.InitializationProvider' -and
            $authority -eq "$NewPackage.androidx-startup") {
            $provider.SetAttribute('authorities', $androidNs, "$NewPackage.honeyboard-androidx-startup")
        }
    }
    $manifest.Save($manifestPath)
}

function Remove-ManifestComponents([string] $DecodedRoot, [scriptblock] $KeepComponent) {
    $manifestPath = Join-Path $DecodedRoot 'AndroidManifest.xml'
    [xml] $manifest = Get-Content -LiteralPath $manifestPath -Raw
    foreach ($component in @($manifest.SelectNodes('/manifest/application/*'))) {
        if (@('activity', 'activity-alias', 'receiver', 'service', 'provider') -notcontains $component.LocalName) { continue }
        $name = Get-ComponentName $component $oldPackage
        if (-not (& $KeepComponent $component.LocalName $name)) {
            [void]$component.ParentNode.RemoveChild($component)
        }
    }
    $manifest.Save($manifestPath)
    return $manifest
}

function Build-SignedApk([string] $DecodedRoot, [string] $Name, [string] $OutputPath) {
    $unsigned = Join-Path $work "$Name-unsigned.apk"
    $aligned = Join-Path $work "$Name-aligned.apk"
    Invoke-Apktool @('b', $DecodedRoot, '-f', '-p', $FrameworkDir, '-o', $unsigned) "$Name APK rebuild failed."
    & $zipalign -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw "$Name zipalign failed." }
    & $apksigner sign --ks $DebugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputPath $aligned
    if ($LASTEXITCODE -ne 0) { throw "$Name signing failed." }
    & $apksigner verify --print-certs $OutputPath
    if ($LASTEXITCODE -ne 0) { throw "$Name signature verification failed." }
    Remove-Item -LiteralPath $DecodedRoot, $unsigned, $aligned -Recurse -Force
}

function Assert-UniqueProviderAuthorities([string] $DecodedRoot, [string] $ExpectedPackage) {
    [xml] $manifest = Get-Content -LiteralPath (Join-Path $DecodedRoot 'AndroidManifest.xml') -Raw
    if ($manifest.manifest.GetAttribute('package') -ne $ExpectedPackage) {
        throw "Manifest package mismatch in $DecodedRoot"
    }
    $seen = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($provider in $manifest.SelectNodes('//application/provider')) {
        $component = Get-ComponentName $provider $ExpectedPackage
        $rawAuthorities = $provider.GetAttribute('authorities', $androidNs)
        foreach ($authority in ($rawAuthorities -split '[;,]')) {
            $authority = $authority.Trim()
            if (-not $authority) { continue }
            if ($seen.ContainsKey($authority)) {
                throw "Duplicate provider authority '$authority': $($seen[$authority]) and $component"
            }
            $seen.Add($authority, $component)
        }
    }
    return @($seen.Keys)
}

function Restore-LauncherRoutineProviderAuthority([string] $DecodedRoot) {
    # One UI Home queries this stable Nemotron authority from its launcher
    # process. Keep the provider owned by Core, but preserve the URI consumed
    # by the UI package after the split.
    $manifestPath = Join-Path $DecodedRoot 'AndroidManifest.xml'
    [xml] $manifest = Get-Content -LiteralPath $manifestPath -Raw
    foreach ($provider in $manifest.SelectNodes('//application/provider')) {
        if ($provider.GetAttribute('name', $androidNs) -eq
            'com.samsung.android.sdk.routines.v3.internal.RoutineSdkContentProvider') {
            $provider.SetAttribute('authorities', $androidNs, "$oldPackage.provider.routines.v3")
        }
    }
    $manifest.Save($manifestPath)
}

function Assert-NoCrossPackageAuthorityDuplicates([hashtable] $AuthoritySets) {
    $owners = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($package in $AuthoritySets.Keys) {
        foreach ($authority in @($AuthoritySets[$package])) {
            if ([string]::IsNullOrWhiteSpace($authority)) { continue }
            if ($owners.ContainsKey($authority)) {
                throw "Provider authority '$authority' is shared by packages '$($owners[$authority])' and '$package'."
            }
            $owners.Add($authority, $package)
        }
    }
}

function Test-KeyboardComponent([string] $ComponentType, [string] $Name) {
    if ($ComponentType -notin @('activity', 'activity-alias', 'receiver', 'service', 'provider')) { return $false }
    return $Name -match '^com\.samsung\.android\.(honeyboard|writingtoolkit|fontchooser)\.' -or
        $Name -match '^com\.nemotron\.honeyboard\.shaded\.' -or
        $Name -eq 'com.nemotron.voiceime.record.VoiceRecordService'
}

function Set-CoreApplicationBase([string] $DecodedRoot) {
    $file = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'NemotronApp.smali' | Select-Object -First 1
    if (-not $file) { throw 'Core NemotronApp class was not found.' }
    $text = [IO.File]::ReadAllText($file.FullName)
    $expected = '.super Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;'
    if (-not $text.Contains($expected)) { throw 'NemotronApp no longer extends the merged HoneyBoard application as expected.' }
    $text = $text.Replace($expected, '.super Landroid/app/Application;')
    $text = $text.Replace(
        'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;-><init>()V',
        'Landroid/app/Application;-><init>()V')
    $text = $text.Replace(
        'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;->onCreate()V',
        'Landroid/app/Application;->onCreate()V')
    $text = $text.Replace(
        'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;',
        'Landroid/app/Application;')
    [IO.File]::WriteAllText($file.FullName, $text, [Text.UTF8Encoding]::new($false))
    if ($text.Contains('HoneyBoardApplication;')) { throw 'Core application still references HoneyBoardApplication.' }
}

function Assert-CorePermissionReferences([string] $DecodedRoot) {
    [xml] $manifest = Get-Content -LiteralPath (Join-Path $DecodedRoot 'AndroidManifest.xml') -Raw
    $declared = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($permission in $manifest.SelectNodes('/manifest/permission')) {
        [void]$declared.Add($permission.GetAttribute('name', $androidNs))
    }
    foreach ($node in $manifest.SelectNodes('/manifest/application//*[@*] | /manifest/application/*[@*]')) {
        foreach ($attribute in $node.Attributes) {
            if ($attribute.LocalName -notin @('permission', 'readPermission', 'writePermission', 'permissionGroup')) { continue }
            if ($attribute.Value.StartsWith("$oldPackage.honeyboard.permission.", [StringComparison]::Ordinal)) {
                throw "Core still references old keyboard permission $($attribute.Value) on $($node.LocalName)."
            }
            if ($attribute.Value.StartsWith("$corePackage.honeyboard.permission.", [StringComparison]::Ordinal) -and
                -not $declared.Contains($attribute.Value)) {
                throw "Core references an undeclared keyboard permission $($attribute.Value)."
            }
        }
    }
}

try {
    Invoke-Apktool @('d', $CoreInputApk, '-p', $FrameworkDir, '-t', 'A54', '-o', $coreTree, '-f') 'Could not decode Core input.'
    [xml] $coreManifest = Get-Content -LiteralPath (Join-Path $coreTree 'AndroidManifest.xml') -Raw
    if ($coreManifest.manifest.GetAttribute('package') -ne $oldPackage) {
        throw 'Core input must be built from com.nemotron.voiceime2.'
    }

    # Keep a complete inventory of the original Nemotron/HoneyBoard components.
    $coreComponents = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($component in $coreManifest.SelectNodes('/manifest/application/*')) {
        if (@('activity', 'activity-alias', 'receiver', 'service', 'provider') -notcontains $component.LocalName) { continue }
        $name = Get-ComponentName $component $oldPackage
        if ($name) { [void]$coreComponents.Add("$($component.LocalName)|$name") }
    }

    # Core keeps Nemotron services/settings but no IME declarations.
    $coreManifest = Remove-ManifestComponents $coreTree {
        param($type, $name) -not (Test-KeyboardComponent $type $name)
    }
    $application = $coreManifest.SelectSingleNode('/manifest/application')
    $application.SetAttribute('label', $androidNs, 'Nemotron')
    $coreManifest.Save((Join-Path $coreTree 'AndroidManifest.xml'))
    Rewrite-PackageReferences $coreTree $corePackage
    Restore-LauncherRoutineProviderAuthority $coreTree
    Set-CoreApplicationBase $coreTree
    $coreAuthorities = Assert-UniqueProviderAuthorities $coreTree $corePackage
    Assert-CorePermissionReferences $coreTree
    $coreImeFound = $false
    foreach ($service in $coreManifest.SelectNodes('/manifest/application/service')) {
        if ((Get-ComponentName $service $oldPackage) -eq 'com.samsung.android.honeyboard.service.HoneyBoardService') { $coreImeFound = $true }
    }
    if ($coreImeFound) { throw 'Core still declares the Nemotron Keyboard IME service.' }
    Build-SignedApk $coreTree 'core' $coreOutput

    # A dedicated package/UID owns all HoneyBoard components and preferences.
    Invoke-Apktool @('d', $CoreInputApk, '-p', $FrameworkDir, '-t', 'A54', '-o', $keyboardTree, '-f') 'Could not decode Keyboard input.'
    [xml] $keyboardManifest = Remove-ManifestComponents $keyboardTree {
        param($type, $name) Test-KeyboardComponent $type $name
    }
    $keyboardApplication = $keyboardManifest.SelectSingleNode('/manifest/application')
    $keyboardApplication.SetAttribute('name', $androidNs, 'com.samsung.android.honeyboard.app.HoneyBoardApplication')
    $keyboardApplication.SetAttribute('label', $androidNs, 'Nemotron Keyboard')
    $keyboardManifest.Save((Join-Path $keyboardTree 'AndroidManifest.xml'))
    Rewrite-PackageReferences $keyboardTree $keyboardPackage
    $keyboardAuthorities = Assert-UniqueProviderAuthorities $keyboardTree $keyboardPackage
    $keyboardImeFound = $false
    foreach ($service in $keyboardManifest.SelectNodes('/manifest/application/service')) {
        if ((Get-ComponentName $service $oldPackage) -eq 'com.samsung.android.honeyboard.service.HoneyBoardService') { $keyboardImeFound = $true }
    }
    if (-not $keyboardImeFound) { throw 'Nemotron Keyboard APK lost HoneyBoardService.' }
    Build-SignedApk $keyboardTree 'keyboard' $keyboardOutput

    # Decode/build the much larger launcher only after the Core working tree
    # has been released. This keeps peak disk use bounded on the user's D: drive.
    Invoke-Apktool @('d', $UiInputApk, '-p', $FrameworkDir, '-t', 'A54', '-o', $uiTree, '-f') 'Could not decode UI input.'
    [xml] $uiManifest = Get-Content -LiteralPath (Join-Path $uiTree 'AndroidManifest.xml') -Raw
    if ($uiManifest.manifest.GetAttribute('package') -ne $uiPackage) {
        throw 'UI input must be built from com.nemotron.voiceime2.'
    }

    # The existing UI package/data directory and Home role stay in place.
    # Remove keyboard/Core services and providers from its manifest, while
    # retaining the two app-picker screens used by the launcher.
    $uiManifestPath = Join-Path $uiTree 'AndroidManifest.xml'
    [xml] $uiManifest = Get-Content -LiteralPath $uiManifestPath -Raw
    $uiRoot = $uiManifest.manifest
    foreach ($component in @($uiManifest.SelectNodes('/manifest/application/*'))) {
        if (@('activity', 'activity-alias', 'receiver', 'service', 'provider') -notcontains $component.LocalName) { continue }
        $name = Get-ComponentName $component $uiPackage
        if (-not $name) { continue }
        if ($shortcutComponents -contains $name) { continue }
        if ($coreComponents.Contains("$($component.LocalName)|$name")) {
            [void]$component.ParentNode.RemoveChild($component)
        }
    }
    $remainingCoreComponents = [Collections.Generic.List[string]]::new()
    $remainingShortcuts = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($component in $uiManifest.SelectNodes('/manifest/application/*')) {
        if (@('activity', 'activity-alias', 'receiver', 'service', 'provider') -notcontains $component.LocalName) { continue }
        $name = Get-ComponentName $component $uiPackage
        if ($shortcutComponents -contains $name) { [void]$remainingShortcuts.Add($name) }
        if ($coreComponents.Contains("$($component.LocalName)|$name") -and $shortcutComponents -notcontains $name) {
            $remainingCoreComponents.Add("$($component.LocalName)|$name")
        }
    }
    if ($remainingCoreComponents.Count -gt 0) {
        throw "UI APK still declares Core components: $($remainingCoreComponents -join ', ')"
    }
    foreach ($shortcut in $shortcutComponents) {
        if (-not $remainingShortcuts.Contains($shortcut)) { throw "UI APK lost required app picker component: $shortcut" }
    }
    $application = $uiManifest.SelectSingleNode('/manifest/application')
    $application.SetAttribute('name', $androidNs, 'com.sec.android.app.launcher.LauncherApplication')
    $application.SetAttribute('label', $androidNs, 'Nemotron UI')
    foreach ($alias in $uiManifest.SelectNodes('/manifest/application/activity-alias')) {
        if ((Get-ComponentName $alias $uiPackage) -eq 'com.sec.android.app.launcher.activities.LauncherActivity') {
            $alias.SetAttribute('label', $androidNs, 'Nemotron UI')
        }
    }
    $uiManifest.Save($uiManifestPath)
    $uiAuthorities = Assert-UniqueProviderAuthorities $uiTree $uiPackage
    Assert-NoCrossPackageAuthorityDuplicates @{
        $corePackage = $coreAuthorities
        $keyboardPackage = $keyboardAuthorities
        $uiPackage = $uiAuthorities
    }

    $homeFound = $false
    foreach ($component in $uiManifest.SelectNodes('/manifest/application/activity | /manifest/application/activity-alias')) {
        $name = Get-ComponentName $component $uiPackage
        $hasMain = $false
        $hasHome = $false
        foreach ($action in $component.SelectNodes('./intent-filter/action')) {
            if ($action.GetAttribute('name', $androidNs) -eq 'android.intent.action.MAIN') { $hasMain = $true }
        }
        foreach ($category in $component.SelectNodes('./intent-filter/category')) {
            if ($category.GetAttribute('name', $androidNs) -eq 'android.intent.category.HOME') { $hasHome = $true }
        }
        if ($hasMain -and $hasHome -and $name -like 'com.sec.android.app.launcher.*') {
            $homeFound = $true
            break
        }
    }
    if (-not $homeFound) { throw 'UI APK lost the One UI HOME activity.' }
    if ($application.GetAttribute('name', $androidNs) -ne 'com.sec.android.app.launcher.LauncherApplication') {
        throw 'UI APK must start the launcher Application directly, without Nemotron Core startup.'
    }

    Build-SignedApk $uiTree 'ui' $uiOutput
    foreach ($apk in @($coreOutput, $keyboardOutput, $uiOutput)) {
        & $apksigner verify --print-certs $apk
        if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $apk" }
    }
    $coreSigner = Get-SignerDigest $coreOutput
    $keyboardSigner = Get-SignerDigest $keyboardOutput
    $uiSigner = Get-SignerDigest $uiOutput
    if ($coreSigner -ne $uiSigner -or $keyboardSigner -ne $uiSigner -or $uiSigner -ne $uiInputSigner) {
        throw "Signer mismatch: UI input=$uiInputSigner, Core=$coreSigner, Keyboard=$keyboardSigner, UI=$uiSigner. Existing app data cannot be safely updated."
    }

    # Re-read the built manifests as a final assertion against packaging drift.
    foreach ($apk in @($coreOutput, $keyboardOutput, $uiOutput)) {
        $badging = @(& $aapt dump badging $apk)
        if ($LASTEXITCODE -ne 0) { throw "Could not inspect built APK: $apk" }
        $expected = if ($apk -eq $coreOutput) { $corePackage } elseif ($apk -eq $keyboardOutput) { $keyboardPackage } else { $uiPackage }
        $expectedPackageLine = "^package: name='$([regex]::Escape($expected))'"
        if (-not ($badging | Where-Object { $_ -match $expectedPackageLine })) {
            throw "Built APK has the wrong application ID: $apk"
        }
    }
    Write-Host "Core package: $coreOutput"
    Write-Host "Keyboard package: $keyboardOutput"
    Write-Host "UI package (existing home data retained on update): $uiOutput"
    Remove-Item -LiteralPath $work -Recurse -Force
    Write-Host 'Temporary decoded trees and unsigned files removed.'
} catch {
    Write-Error "Split build stopped safely. No device was changed. Working files: $work`n$($_.Exception.Message)"
    throw
}
