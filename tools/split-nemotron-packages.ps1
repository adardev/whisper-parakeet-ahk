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
$launcherRoutineProvider = 'com.samsung.android.sdk.routines.v3.internal.RoutineSdkContentProvider'
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
$coreOutputDirectory = Join-Path $OutputDirectory 'Nemotron'
$keyboardOutputDirectory = Join-Path $OutputDirectory 'Nemotron-Keyboard'
$uiOutputDirectory = Join-Path $OutputDirectory 'Nemotron-UI'
foreach ($directory in @($coreOutputDirectory, $keyboardOutputDirectory, $uiOutputDirectory)) {
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
}
$coreOutput = Join-Path $coreOutputDirectory 'Nemotron-Core.apk'
$keyboardOutput = Join-Path $keyboardOutputDirectory 'Nemotron-Keyboard.apk'
$uiOutput = Join-Path $uiOutputDirectory 'Nemotron-UI.apk'
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

function Allow-HoneyBoardSelfProviderCalls([string] $DecodedRoot) {
    # The cloned app has a development signing key, so HoneyBoard's own
    # signature allowlist rejects its internal ContentProvider calls. Permit
    # the provider's actual host app identity while retaining the Samsung
    # certificate check for every external caller.
    $providerGuard = $null
    foreach ($file in Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'b.smali') {
        $candidate = [IO.File]::ReadAllText($file.FullName)
        if ($candidate.Contains('.class public abstract Li11/b;')) {
            $providerGuard = $file
            $code = $candidate
            break
        }
    }
    if (-not $providerGuard) { throw 'HoneyBoard clipboard provider signature guard was not found.' }
    $marker = 'nemotron_self_provider_access'
    if (-not $code.Contains($marker)) {
        $pattern = '(?m)^(\.method public static a\(Landroid/content/Context;Ljava/lang/String;\)Z\r?\n    \.locals 5\r?\n\r?\n    const/4 v0, 0x0\r?\n)'
        $matches = [regex]::Matches($code, $pattern)
        if ($matches.Count -ne 1) { throw 'HoneyBoard provider signature guard layout changed; refusing to patch it.' }
        $code = [regex]::Replace($code, $pattern, {
            param($match)
            $match.Groups[1].Value + @'
    if-eqz p0, :nemotron_self_provider_access_external
    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v1, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :nemotron_self_provider_access_external
    const/4 v0, 0x1
    return v0
:nemotron_self_provider_access_external
'@
        }, 1)
        [IO.File]::WriteAllText($providerGuard.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
    if (-not $code.Contains($marker)) {
        # Keep a stable marker next to the injected fast path for rebuild audits.
        $code = $code.Replace(':nemotron_self_provider_access_external', ':nemotron_self_provider_access_external # nemotron_self_provider_access')
        [IO.File]::WriteAllText($providerGuard.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
    if (-not $code.Contains(':nemotron_self_provider_access_external')) {
        throw 'HoneyBoard self-provider access patch was not written.'
    }
    if (-not $code.Contains('Landroid/os/Binder;->getCallingUid()I')) {
        $oldSelfCheck = @'
    invoke-virtual {v1, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :nemotron_self_provider_access_external
    const/4 v0, 0x1
    return v0
'@
        $newSelfCheck = @'
    invoke-virtual {v1, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_same_uid_provider_call
    if-nez p1, :nemotron_self_provider_access_external
    invoke-static {}, Landroid/os/Binder;->getCallingUid()I
    move-result v2
    invoke-static {}, Landroid/os/Process;->myUid()I
    move-result v3
    if-ne v2, v3, :nemotron_self_provider_access_external
:nemotron_same_uid_provider_call
    const/4 v0, 0x1
    return v0
'@
        if (-not $code.Contains($oldSelfCheck)) { throw 'HoneyBoard provider caller identity check changed; refusing to extend it.' }
        $code = $code.Replace($oldSelfCheck, $newSelfCheck)
        [IO.File]::WriteAllText($providerGuard.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
}

function Enable-NemotronClipboardHistoryBridge([string] $DecodedRoot) {
    $smaliDir = Join-Path $DecodedRoot 'smali_classes14'
    $bridgeDir = Join-Path $smaliDir 'com/nemotron/keyboard'
    New-Item -ItemType Directory -Path $bridgeDir -Force | Out-Null
    $bridgeSource = Join-Path $PSScriptRoot '..\apps\nemotron-keyboard\build\ClipboardHistoryBridge.smali'
    Copy-Item -LiteralPath $bridgeSource -Destination (Join-Path $bridgeDir 'ClipboardHistoryBridge.smali') -Force

    $query = $null
    foreach ($file in Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 's.smali') {
        $candidate = [IO.File]::ReadAllText($file.FullName)
        if ($candidate.Contains('.class public final Le2/s;') -and $candidate.Contains('RichcontentProvider/clipboard')) {
            $query = $file
            $code = $candidate
            break
        }
    }
    if (-not $query) { throw 'Nemotron clipboard-history query coroutine was not found.' }
    if (-not $code.Contains('nemotron_clipboard_history_bridge')) {
        $needle = '    iget-object v5, v5, Le2/t;->a:Landroid/content/Context;'
        if (-not $code.Contains($needle)) { throw 'Clipboard-history bridge hook point changed; refusing to patch it.' }
        $replacement = $needle + @'

    # nemotron_clipboard_history_bridge
    invoke-static {v5}, Lcom/nemotron/keyboard/ClipboardHistoryBridge;->syncPrimaryClip(Landroid/content/Context;)V
'@
        $code = $code.Replace($needle, $replacement.TrimEnd())
        [IO.File]::WriteAllText($query.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
}

function Patch-HoneyBoardMaintenanceUserCheck([string] $DecodedRoot) {
    $helper = $null
    foreach ($file in Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'b.smali') {
        $candidate = [IO.File]::ReadAllText($file.FullName)
        if ($candidate.Contains('.class public final Lmo/b;')) {
            $helper = $file
            $code = $candidate
            break
        }
    }
    if (-not $helper) { throw 'HoneyBoard maintenance-mode helper was not found.' }
    if ($code.Contains('Landroid/app/ActivityManager;->semGetCurrentUser()I')) {
        $replacement = @'
.method public final c()Z
    .locals 2
    invoke-static {}, Landroid/os/UserHandle;->semGetMyUserId()I
    move-result v1
    const/16 v0, 0x4d
    if-ne v1, v0, :nemotron_not_maintenance_user
    const/4 v0, 0x1
    return v0
    :nemotron_not_maintenance_user
    const/4 v0, 0x0
    return v0
.end method
'@
        $pattern = '(?ms)^\.method public final c\(\)Z\r?\n.*?^\.end method'
        if ([regex]::Matches($code, $pattern).Count -ne 1) { throw 'HoneyBoard maintenance-mode check changed; refusing to patch it.' }
        $code = [regex]::Replace($code, $pattern, $replacement.TrimEnd(), 1)
        [IO.File]::WriteAllText($helper.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
    if ($code.Contains('Landroid/app/ActivityManager;->semGetCurrentUser()I')) {
        throw 'HoneyBoard maintenance-mode check still uses the privileged current-user API.'
    }
}

function Patch-HoneyBoardClipboardSelfAccess([string] $DecodedRoot) {
    $store = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'l.smali' |
        Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.class public final Lq2/l;') } |
        Select-Object -First 1
    if (-not $store) { throw 'HoneyBoard clipboard store was not found.' }
    $code = [IO.File]::ReadAllText($store.FullName)
    $needle = @'
.method public final f(Ljava/lang/String;)Z
    .locals 2

'@
    $replacement = @'
.method public final f(Ljava/lang/String;)Z
    .locals 2

    const-string v0, "com.nemotron.keyboard"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :nemotron_clipboard_allowlist

    const/4 p0, 0x1

    return p0

    :nemotron_clipboard_allowlist
'@
    if (-not $code.Contains($needle)) {
        if ($code.Contains(':nemotron_clipboard_allowlist')) { return }
        throw 'HoneyBoard clipboard caller allowlist hook changed; refusing to patch it.'
    }
    $code = $code.Replace($needle, $replacement)
    [IO.File]::WriteAllText($store.FullName, $code, [Text.UTF8Encoding]::new($false))
}

function Patch-HoneyBoardClipboardDeleteDialog([string] $DecodedRoot) {
    $dialog = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'd.smali' |
        Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.class public final Las0/d;') } |
        Select-Object -First 1
    $view = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -Filter 'r.smali' |
        Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.class public final Lo3/r;') } |
        Select-Object -First 1
    if (-not $dialog -or -not $view) { throw 'HoneyBoard clipboard delete dialog classes were not found.' }

    $dialogCode = [IO.File]::ReadAllText($dialog.FullName)
    $dialogCode = $dialogCode.Replace('const/16 v1, 0x7dc', 'const/16 v1, 0x3eb # nemotron_attached_ime_dialog')
    if (-not $dialogCode.Contains('nemotron_attached_ime_dialog')) {
        throw 'Clipboard confirmation dialog type hook changed; refusing to patch it.'
    }
    [IO.File]::WriteAllText($dialog.FullName, $dialogCode, [Text.UTF8Encoding]::new($false))

    $viewCode = [IO.File]::ReadAllText($view.FullName)
    if (-not $viewCode.Contains('nemotron_clipboard_dialog_token')) {
        $needle = @'
    iput-object v0, p0, Lo3/r;->q:Las0/d;

    invoke-virtual {v0}, Landroid/app/Dialog;->show()V
'@
        $replacement = @'
    iput-object v0, p0, Lo3/r;->q:Las0/d;

    # Attach confirmation to the live IME window; Android 16 rejects
    # TYPE_INPUT_METHOD_DIALOG for this cloned, non-platform-signed package.
    invoke-virtual {v0}, Landroid/app/Dialog;->getWindow()Landroid/view/Window;
    move-result-object v1
    if-eqz v1, :nemotron_clipboard_dialog_show
    invoke-virtual {p0}, Landroid/view/View;->getWindowToken()Landroid/os/IBinder;
    move-result-object v2
    if-eqz v2, :nemotron_clipboard_dialog_show
    invoke-virtual {v1}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;
    move-result-object v3
    iput-object v2, v3, Landroid/view/WindowManager$LayoutParams;->token:Landroid/os/IBinder;
    invoke-virtual {v1, v3}, Landroid/view/Window;->setAttributes(Landroid/view/WindowManager$LayoutParams;)V
    :nemotron_clipboard_dialog_show
    # nemotron_clipboard_dialog_token
    invoke-virtual {v0}, Landroid/app/Dialog;->show()V
'@
        if (-not $viewCode.Contains($needle)) { throw 'Clipboard confirmation show hook changed; refusing to patch it.' }
        $viewCode = $viewCode.Replace($needle, $replacement)
        [IO.File]::WriteAllText($view.FullName, $viewCode, [Text.UTF8Encoding]::new($false))
    }
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

function Disable-HoneyBoardStatisticsJobs([string] $DecodedRoot) {
    # HoneyBoard's two periodic usage-statistics jobs are not needed by
    # Nemotron Keyboard. The integrated Home build has this patch too, but the
    # split Keyboard APK is rebuilt from CoreInput and must receive it here.
    foreach ($job in @(
        @{ File = 'b.smali'; Descriptor = 'Lvi/b;'; ContextGetter = 'a'; JobId = 1 },
        @{ File = 'c.smali'; Descriptor = 'Lzi/c;'; ContextGetter = 'a'; JobId = 2 }
    )) {
        $classFile = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter $job.File |
            Where-Object { [regex]::IsMatch([IO.File]::ReadAllText($_.FullName), "(?m)^\.class .*" + [regex]::Escape($job.Descriptor)) } |
            Select-Object -First 1
        if (-not $classFile) { throw "Missing HoneyBoard statistics scheduler $($job.Descriptor)" }
        $code = [IO.File]::ReadAllText($classFile.FullName)
        if (-not $code.Contains(':nemotron_disable_stats_jobs')) {
            $replacement = @"
.method public onCreate()V
    .locals 3
    :nemotron_disable_stats_jobs
    invoke-super {p0}, Loo/b;->onCreate()V
    invoke-direct {p0}, $($job.Descriptor)->$($job.ContextGetter)()Landroid/content/Context;
    move-result-object v0
    const-string v1, "jobscheduler"
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    instance-of v1, v0, Landroid/app/job/JobScheduler;
    if-eqz v1, :nemotron_stats_jobs_done
    check-cast v0, Landroid/app/job/JobScheduler;
    const/4 v1, $($job.JobId)
    invoke-virtual {v0, v1}, Landroid/app/job/JobScheduler;->cancel(I)V
    :nemotron_stats_jobs_done
    return-void
.end method
"@
            $code = [regex]::Replace($code, '(?ms)^\.method public onCreate\(\)V.*?^\.end method', $replacement, 1)
            if ($code -notmatch ':nemotron_disable_stats_jobs') { throw "Could not patch $($job.Descriptor)" }
            [IO.File]::WriteAllText($classFile.FullName, $code, [Text.UTF8Encoding]::new($false))
        }
    }
}

function Patch-RxJavaPurgeInterval([string] $DecodedRoot) {
    # RxJava's purge worker otherwise wakes once per second to scan scheduled
    # executors. Keep the cleanup, with the same 60-second interval used by UI.
    $rxPurge = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' |
        Where-Object {
            $code = [IO.File]::ReadAllText($_.FullName)
            $code.Contains('rx2.purge-period-seconds') -and $code.Contains('Lrv0/k$a;->b:I')
        } | Select-Object -First 1
    if (-not $rxPurge) { throw 'RxJava scheduler purge configuration was not found in Keyboard.' }
    $rxCode = [IO.File]::ReadAllText($rxPurge.FullName)
    if (-not $rxCode.Contains('nemotron_rx_purge_60s')) {
        $rxPattern = '(?m)(^\s*:cond_1\s*\r?\n)(\s*)(iput v2, p0, Lrv0/k\$a;->b:I)'
        $matches = [regex]::Matches($rxCode, $rxPattern)
        if ($matches.Count -ne 1) { throw 'Keyboard RxJava purge default layout changed; refusing to patch it.' }
        $rxCode = [regex]::Replace($rxCode, $rxPattern, {
            param($match)
            $newline = if ($match.Value.Contains("`r`n")) { "`r`n" } else { "`n" }
            $match.Groups[1].Value + $match.Groups[2].Value + 'const/16 v2, 0x3c' + $newline +
                $match.Groups[2].Value + $match.Groups[3].Value + ' # nemotron_rx_purge_60s'
        }, 1)
        [IO.File]::WriteAllText($rxPurge.FullName, $rxCode, [Text.UTF8Encoding]::new($false))
    }
    if (-not $rxCode.Contains('nemotron_rx_purge_60s')) {
        throw 'Keyboard RxJava purge interval marker was not written.'
    }
}

function Test-KeyboardComponent([string] $ComponentType, [string] $Name) {
    if ($ComponentType -notin @('activity', 'activity-alias', 'receiver', 'service', 'provider')) { return $false }
    return $Name -match '^com\.samsung\.android\.(honeyboard|writingtoolkit|fontchooser)\.' -or
        $Name -match '^com\.nemotron\.honeyboard\.shaded\.' -or
        $Name -in @(
            'com.nemotron.voiceime.record.VoiceRecordService',
            'com.nemotron.voiceime.ui.KeyboardMicrophonePermissionActivity'
        )
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
    $routineProviderNode = $null
    foreach ($provider in $coreManifest.SelectNodes('/manifest/application/provider')) {
        if ($provider.GetAttribute('name', $androidNs) -eq $launcherRoutineProvider) {
            $routineProviderNode = $provider
            break
        }
    }
    if (-not $routineProviderNode) { throw 'Samsung Routines provider was not found in Core input.' }
    $routineProviderNode = $routineProviderNode.CloneNode($true)
    $routineProviderNode.SetAttribute('authorities', $androidNs, "$oldPackage.provider.routines.v3")

    # Core keeps Nemotron services/settings but no IME or launcher Routines provider.
    $coreManifest = Remove-ManifestComponents $coreTree {
        param($type, $name) (-not (Test-KeyboardComponent $type $name)) -and
            -not ($type -eq 'provider' -and $name -eq $launcherRoutineProvider)
    }
    $application = $coreManifest.SelectSingleNode('/manifest/application')
    $application.SetAttribute('label', $androidNs, 'Nemotron')
    $coreManifest.Save((Join-Path $coreTree 'AndroidManifest.xml'))
    Rewrite-PackageReferences $coreTree $corePackage
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
    Allow-HoneyBoardSelfProviderCalls $keyboardTree
    Enable-NemotronClipboardHistoryBridge $keyboardTree
    Patch-HoneyBoardMaintenanceUserCheck $keyboardTree
    Patch-HoneyBoardClipboardSelfAccess $keyboardTree
    Patch-HoneyBoardClipboardDeleteDialog $keyboardTree
    $keyboardAuthorities = Assert-UniqueProviderAuthorities $keyboardTree $keyboardPackage
    $keyboardImeFound = $false
    foreach ($service in $keyboardManifest.SelectNodes('/manifest/application/service')) {
        if ((Get-ComponentName $service $oldPackage) -eq 'com.samsung.android.honeyboard.service.HoneyBoardService') { $keyboardImeFound = $true }
    }
    if (-not $keyboardImeFound) { throw 'Nemotron Keyboard APK lost HoneyBoardService.' }
    Disable-HoneyBoardStatisticsJobs $keyboardTree
    Patch-RxJavaPurgeInterval $keyboardTree
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
        if ($shortcutComponents -contains $name -or
            ($component.LocalName -eq 'provider' -and $name -eq $launcherRoutineProvider)) { continue }
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
        if ($coreComponents.Contains("$($component.LocalName)|$name") -and
            $shortcutComponents -notcontains $name -and $name -ne $launcherRoutineProvider) {
            $remainingCoreComponents.Add("$($component.LocalName)|$name")
        }
    }
    if ($remainingCoreComponents.Count -gt 0) {
        throw "UI APK still declares Core components: $($remainingCoreComponents -join ', ')"
    }
    foreach ($shortcut in $shortcutComponents) {
        if (-not $remainingShortcuts.Contains($shortcut)) { throw "UI APK lost required app picker component: $shortcut" }
    }
    $uiRoutineProviders = @($uiManifest.SelectNodes('/manifest/application/provider') | Where-Object {
        (Get-ComponentName $_ $uiPackage) -eq $launcherRoutineProvider
    })
    foreach ($provider in $uiRoutineProviders) { [void]$provider.ParentNode.RemoveChild($provider) }
    [void]$uiManifest.SelectSingleNode('/manifest/application').AppendChild(
        $uiManifest.ImportNode($routineProviderNode, $true))
    foreach ($provider in $uiManifest.SelectNodes('/manifest/application/provider')) {
        $name = Get-ComponentName $provider $uiPackage
        if ($name -eq $launcherRoutineProvider -or
            $name -eq 'com.nemotron.oneui.shaded.com.samsung.android.sdk.routines.v3.internal.RoutineSdkContentProvider') {
            # This is an app-signed launcher, not Samsung's privileged Home.
            # The vendor signature permission is unavailable to it, so its own
            # Routines SDK cannot even notify its provider without crashing.
            $provider.RemoveAttribute('permission', $androidNs)
        }
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
