param(
    [Parameter(Mandatory = $true)] [string] $HoneyBoardApk,
    [Parameter(Mandatory = $true)] [string] $NemotronApk,
    [Parameter(Mandatory = $true)] [string] $OutputApk,
    [string] $ApktoolJar = "$env:USERPROFILE\Downloads\Honeyboard_5.9.30.88\tools\apktool_2.12.1.jar",
    [string] $AndroidSdk = "$env:ANDROID_HOME",
    [string] $JavaExe = "$env:JAVA_HOME\bin\java.exe"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

foreach ($file in @($HoneyBoardApk, $NemotronApk, $ApktoolJar, $JavaExe)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Required file was not found: $file"
    }
}
if (-not $AndroidSdk) { throw 'ANDROID_HOME must point to the Android SDK.' }
if (Test-Path -LiteralPath $OutputApk) { throw "Output already exists; choose a new path: $OutputApk" }

$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools found under $AndroidSdk" }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$debugKeystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
foreach ($file in @($apksigner, $zipalign, $aapt, $debugKeystore)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Required signing tool/file missing: $file" }
}

$outDir = Split-Path -Parent $OutputApk
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$work = Join-Path $outDir ('.nemotron-keyboard-build-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$base = Join-Path $work 'nemotron'
$hb = Join-Path $work 'honeyboard'
$unsigned = Join-Path $work 'combined-unsigned.apk'
$aligned = Join-Path $work 'combined-aligned.apk'

& $JavaExe -jar $ApktoolJar d $NemotronApk -o $base -f
if ($LASTEXITCODE -ne 0) { throw 'Could not decode the Nemotron APK.' }
& $JavaExe -jar $ApktoolJar d $HoneyBoardApk -o $hb -f
if ($LASTEXITCODE -ne 0) { throw 'Could not decode the HoneyBoard APK.' }

# Keep Nemotron's resource IDs (and app data) stable. Read complete compiled
# resource tables, including non-public resources, and namespace collisions.
# All HoneyBoard attributes receive new IDs in their original order: its compiled
# styleable arrays must stay sorted or ConstraintLayout and themes break.
$nemPublicPath = Join-Path $base 'res\values\public.xml'
[xml] $nemPublic = Get-Content -LiteralPath $nemPublicPath -Raw
$nemTable = @(& $aapt dump resources $NemotronApk)
if ($LASTEXITCODE -ne 0) { throw 'Could not read the Nemotron resource table.' }
$hbTable = @(& $aapt dump resources $HoneyBoardApk)
if ($LASTEXITCODE -ne 0) { throw 'Could not read the HoneyBoard resource table.' }
function Read-ResourceSpecs([string[]] $lines) {
    $result = [System.Collections.Generic.List[object]]::new()
    foreach ($line in $lines) {
        if ($line -match 'spec resource (0x7f[0-9a-fA-F]{6}) [^:]+:([^/]+)/([^:\s]+):') {
            $result.Add([pscustomobject]@{ Id = $Matches[1].ToLowerInvariant(); Type = $Matches[2]; Name = $Matches[3] })
        }
    }
    return $result
}
$nemSpecs = Read-ResourceSpecs $nemTable
$hbSpecs = Read-ResourceSpecs $hbTable
$nemSymbols = [System.Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
$nemOriginalSymbols = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$typeIds = @{}
$maxEntries = @{}
$usedTypeIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($entry in $nemSpecs) {
    $key = "$($entry.Type)/$($entry.Name)"
    $nemSymbols[$key] = $entry.Id
    [void]$nemOriginalSymbols.Add($key)
    $value = [Convert]::ToUInt32($entry.Id.Substring(2), 16)
    $typeId = [int](($value -shr 16) -band 0xff)
    $entryId = [int]($value -band 0xffff)
    $typeIds[$entry.Type] = $typeId
    $usedTypeIds.Add($typeId) | Out-Null
    if (-not $maxEntries.ContainsKey($entry.Type) -or $entryId -gt $maxEntries[$entry.Type]) {
        $maxEntries[$entry.Type] = $entryId
    }
}

$idMap = @{}
$resourceRenames = [System.Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
$newPublicNodes = [System.Collections.Generic.List[System.Xml.XmlElement]]::new()
foreach ($entry in $hbSpecs) {
    $key = "$($entry.Type)/$($entry.Name)"
    $oldId = $entry.Id
    if ($nemSymbols.ContainsKey($key) -and $entry.Type -ne 'id') {
        $newResourceName = if ($entry.Name.StartsWith('$')) { '$hb_' + $entry.Name.Substring(1) } else { 'hb_' + $entry.Name }
        $resourceRenames[$key] = $entry.Type + '/' + $newResourceName
        $entry.Name = $newResourceName
        $key = "$($entry.Type)/$($entry.Name)"
    } elseif ($nemSymbols.ContainsKey($key)) {
        $idMap[$oldId] = $nemSymbols[$key].ToLowerInvariant()
        continue
    }
    if (-not $typeIds.ContainsKey($entry.Type)) {
        $candidate = 1
        while ($usedTypeIds.Contains($candidate)) { $candidate++ }
        if ($candidate -gt 255) { throw "No resource type IDs remain for $($entry.Type)" }
        $typeIds[$entry.Type] = $candidate
        $maxEntries[$entry.Type] = -1
        $usedTypeIds.Add($candidate) | Out-Null
    }
    $nextEntry = [int]$maxEntries[$entry.Type] + 1
    if ($nextEntry -gt 65535) { throw "Resource entry table overflow for $($entry.Type)" }
    $maxEntries[$entry.Type] = $nextEntry
    $newValue = [uint32]((0x7f -shl 24) -bor ([int]$typeIds[$entry.Type] -shl 16) -bor $nextEntry)
    $newId = '0x{0:x8}' -f $newValue
    $idMap[$oldId] = $newId
    $nemSymbols[$key] = $newId
    $node = $nemPublic.CreateElement('public')
    $node.SetAttribute('type', $entry.Type)
    $node.SetAttribute('name', $entry.Name)
    $node.SetAttribute('id', $newId)
    $newPublicNodes.Add($node)
}
# Include package-renamed provider and permission strings from the original
# Samsung package copy, not the side-by-side clone's temporary package ID.
$oldHbPackage = 'com.samsung.android.honeyboard.patched'
$newAppPackage = 'com.nemotron.voiceime2'
foreach ($node in $newPublicNodes) { [void]$nemPublic.resources.AppendChild($node) }
$nemPublic.Save($nemPublicPath)

# Copy HoneyBoard resources with matching references to their isolated symbols.
$hbRes = Join-Path $hb 'res'
$nemRes = Join-Path $base 'res'
function Rewrite-HbXmlText([string] $xmlText) {
    $xmlText = $xmlText.Replace('androidx.', 'com.nemotron.honeyboard.shaded.androidx.')
    $xmlText = $xmlText.Replace('com.google.android.material.', 'com.nemotron.honeyboard.shaded.com.google.android.material.')
    $xmlText = [regex]::Replace($xmlText, '(<(?:item|attr) name=")([^":]+)(")', {
        param($m)
        $key = 'attr/' + $m.Groups[2].Value
        if ($resourceRenames.ContainsKey($key)) {
            return $m.Groups[1].Value + ($resourceRenames[$key] -split '/', 2)[1] + $m.Groups[3].Value
        }
        return $m.Value
    })
    $xmlText = [regex]::Replace($xmlText, '\b([A-Za-z_]\w*):([A-Za-z_]\w*)(?==)', {
        param($m)
        $key = 'attr/' + $m.Groups[2].Value
        if ($m.Groups[1].Value -ne 'android' -and $resourceRenames.ContainsKey($key)) {
            return $m.Groups[1].Value + ':' + ($resourceRenames[$key] -split '/', 2)[1]
        }
        return $m.Value
    })
    $xmlText = [regex]::Replace($xmlText, '\?([A-Za-z0-9_.]+)(?=[\s"<])', {
        param($m)
        $key = 'attr/' + $m.Groups[1].Value
        if ($resourceRenames.ContainsKey($key)) { return '?' + ($resourceRenames[$key] -split '/', 2)[1] }
        return $m.Value
    })
    return [regex]::Replace($xmlText, '[@?](?:\+)?[A-Za-z_]+/[A-Za-z0-9_.$]+', {
        param($m)
        $resourceKey = $m.Value.TrimStart('@', '+')
        $resourceKey = $resourceKey.TrimStart('?')
        if ($resourceRenames.ContainsKey($resourceKey)) { return $m.Value.Substring(0, 1) + $resourceRenames[$resourceKey] }
        return $m.Value
    })
}
foreach ($sourceFile in Get-ChildItem -LiteralPath $hbRes -Recurse -File) {
    $relative = $sourceFile.FullName.Substring($hbRes.Length + 1)
    if ($relative -match '^values(?:-[^\\]+)?\\') {
        if ($sourceFile.Name -ieq 'public.xml') { continue }
        [xml] $values = Rewrite-HbXmlText ([IO.File]::ReadAllText($sourceFile.FullName))
        foreach ($node in @($values.resources.ChildNodes)) {
            if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
            $type = $node.LocalName
            if ($type -eq 'declare-styleable') { continue }
            if ($type -eq 'item') { $type = $node.GetAttribute('type') }
            if ($type -eq 'integer-array' -or $type -eq 'string-array') { $type = 'array' }
            $name = $node.GetAttribute('name')
            if (-not $type -or -not $name) { continue }
            if ($type -eq 'style') {
                $parent = $node.GetAttribute('parent')
                if ($parent -and $resourceRenames.ContainsKey("style/$parent")) {
                    $node.SetAttribute('parent', ('@' + $resourceRenames["style/$parent"]))
                }
            }
            if ($resourceRenames.ContainsKey("$type/$name")) {
                $node.SetAttribute('name', ($resourceRenames["$type/$name"] -split '/', 2)[1])
                continue
            }
            if ($nemOriginalSymbols.Contains("$type/$name")) {
                [void]$values.resources.RemoveChild($node)
            }
        }
        $targetDir = Split-Path -Parent (Join-Path $nemRes $relative)
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        $target = Join-Path $targetDir ('hb-' + $sourceFile.Name)
        $values.Save($target)
        continue
    }

    $resourceDir = Split-Path -Leaf (Split-Path -Parent $sourceFile.FullName)
    $resourceType = ($resourceDir -split '-', 2)[0]
    $resourceName = [IO.Path]::GetFileNameWithoutExtension($sourceFile.Name) -replace '\.9$', ''
    if ($resourceRenames.ContainsKey("$resourceType/$resourceName")) {
        $newFileName = if ($sourceFile.Name.StartsWith('$')) { '$hb_' + $sourceFile.Name.Substring(1) } else { 'hb_' + $sourceFile.Name }
        $relative = Join-Path $resourceDir $newFileName
    } elseif ($nemOriginalSymbols.Contains("$resourceType/$resourceName")) { continue }
    $target = Join-Path $nemRes $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    if ($sourceFile.Extension -eq '.xml') {
        [IO.File]::WriteAllText($target, (Rewrite-HbXmlText ([IO.File]::ReadAllText($sourceFile.FullName))))
    } else { Copy-Item -LiteralPath $sourceFile.FullName -Destination $target }
}

# Inject HoneyBoard DEX classes and isolate its AndroidX, Material and coroutine
# libraries. Replace the compile-only Application stub with Samsung's class.
$nemSmaliDirs = Get-ChildItem -LiteralPath $base -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' }
$existingClasses = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($dir in $nemSmaliDirs) {
    foreach ($file in Get-ChildItem -LiteralPath $dir.FullName -Recurse -Filter '*.smali') {
        $rel = $file.FullName.Substring($dir.FullName.Length + 1).Replace('\', '/')
        $descriptor = 'L' + $rel.Substring(0, $rel.Length - 6) + ';'
        [void]$existingClasses.Add($descriptor)
    }
}
$hbSmaliDirs = @(Get-ChildItem -LiteralPath $hb -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' } | Sort-Object { if ($_.Name -eq 'smali') { 1 } else { [int]($_.Name -replace '^smali_classes', '') } })
# Some Samsung R constants point at absent resource-table entries. Keep those
# IDs invalid rather than letting them alias an unrelated Nemotron resource.
foreach ($dir in $hbSmaliDirs) {
    foreach ($file in Get-ChildItem -LiteralPath $dir.FullName -Recurse -Filter '*.smali') {
        $content = [IO.File]::ReadAllText($file.FullName)
        foreach ($match in [regex]::Matches($content, '(?<![-\w])0x7f[0-9a-fA-F]{6}(?![0-9a-fA-F])')) {
            $key = $match.Value.ToLowerInvariant()
            if (-not $idMap.ContainsKey($key) -and $file.Name -cmatch '^R(?:\$.*)?\.smali$') { $idMap[$key] = '0x7f000000' }
        }
    }
}
$maxDex = ($nemSmaliDirs | ForEach-Object { if ($_.Name -eq 'smali') { 1 } else { [int]($_.Name -replace '^smali_classes', '') } } | Measure-Object -Maximum).Maximum
$duplicateClassCount = 0
$insertedClassCount = 0
$dictationHooks = 0
$spaceCommitHooks = 0
foreach ($hbDir in $hbSmaliDirs) {
    $maxDex++
    $destDir = Join-Path $base "smali_classes$maxDex"
    New-Item -ItemType Directory -Path $destDir | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $hbDir.FullName -Recurse -Filter '*.smali') {
        $rel = $file.FullName.Substring($hbDir.FullName.Length + 1)
        if ($rel.Replace('\', '/').StartsWith('kotlinx/coroutines/') -or $rel.Replace('\', '/').StartsWith('androidx/') -or $rel.Replace('\', '/').StartsWith('com/google/android/material/')) {
            $rel = 'com\nemotron\honeyboard\shaded\' + $rel
        }
        $descriptor = 'L' + $rel.Replace('\', '/').Substring(0, $rel.Length - 6) + ';'
        if ($descriptor.StartsWith('Lcom/samsung/android/honeyboard/nemotron/NemotronDictationService')) { continue }
        $target = Join-Path $destDir $rel
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        $content = [IO.File]::ReadAllText($file.FullName)
        $content = $content.Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/honeyboard/shaded/kotlinx/coroutines/')
        $content = $content.Replace('kotlinx.coroutines.', 'com.nemotron.honeyboard.shaded.kotlinx.coroutines.')
        $content = $content.Replace('Landroidx/', 'Lcom/nemotron/honeyboard/shaded/androidx/')
        $content = $content.Replace('androidx.', 'com.nemotron.honeyboard.shaded.androidx.')
        $content = $content.Replace('Lcom/google/android/material/', 'Lcom/nemotron/honeyboard/shaded/com/google/android/material/')
        $content = $content.Replace('com.google.android.material.', 'com.nemotron.honeyboard.shaded.com.google.android.material.')
        if ($descriptor -eq 'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;') {
            $stub = Get-ChildItem -LiteralPath $base -Recurse -Filter 'HoneyBoardApplication.smali' | Select-Object -First 1
            if ($stub) { $target = $stub.FullName }
            $content = $content -replace '(?m)^\.class public final Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;', '.class public Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;'
        } elseif ($existingClasses.Contains($descriptor)) {
            $duplicateClassCount++
            continue
        }
        $content = [regex]::Replace($content, '(?<![-\w])0x7f[0-9a-fA-F]{6}(?![0-9a-fA-F])', {
            param($match)
            $key = $match.Value.ToLowerInvariant()
            if ($idMap.ContainsKey($key)) { return $idMap[$key] }
            return $match.Value
        })
        $content = $content.Replace(
            'com.samsung.android.honeyboard.patched.androidx-startup.patched',
            'com.nemotron.voiceime2.androidx-startup')
        $content = $content.Replace($oldHbPackage, $newAppPackage)
        if ($content.Contains('NemotronDictationService;->toggle(Landroid/content/Context;Landroid/view/inputmethod/InputConnection;)V')) {
            $content = $content.Replace(
                'Lcom/samsung/android/honeyboard/nemotron/NemotronDictationService;->toggle(Landroid/content/Context;Landroid/view/inputmethod/InputConnection;)V',
                'Lcom/nemotron/voiceime/record/VoiceRecordService;->toggleFromIme(Landroid/content/Context;Landroid/view/inputmethod/InputConnection;Landroid/view/View;Landroid/view/MotionEvent;)V')
            $content = $content.Replace('invoke-static {v1, v0}, Lcom/nemotron/voiceime/record/VoiceRecordService;->toggleFromIme', 'invoke-static {v1, v0, p0, p1}, Lcom/nemotron/voiceime/record/VoiceRecordService;->toggleFromIme')
            $dictationHooks++
        }
        if ($descriptor -eq 'Lcom/samsung/android/honeyboard/keyboard/view/HoneyBoardLayout;') {
            $method = @'
.method private final handleNemotronDoubleSpace(Landroid/view/MotionEvent;)Z
    .locals 1
    invoke-static {p0}, Lcom/nemotron/voiceime/record/VoiceRecordService;->bindHoneyBoardKeyboard(Landroid/view/View;)V
    const/4 v0, 0x0
    return v0
.end method
'@
            $content = [regex]::Replace($content, '(?s)\.method private final handleNemotronDoubleSpace\(Landroid/view/MotionEvent;\)Z.*?\.end method', $method)
        }
        if ($descriptor -eq 'Lii/a;') {
            $hook = @'
    invoke-static {p0, p1}, Lcom/nemotron/voiceime/record/VoiceRecordService;->onHoneyBoardSpaceCommit(Landroid/view/inputmethod/InputConnection;Ljava/lang/CharSequence;)Z
    move-result v3
    if-eqz v3, :nemotron_space_continue
    const/4 v4, 0x1
    return v4
    :nemotron_space_continue
'@
            $pattern = '(?s)(\.method public commitText\(Ljava/lang/CharSequence;I\)Z.*?iget v3, p0, Lii/a;->k:I\s+if-nez v3, :cond_2)'
            if (-not [regex]::IsMatch($content, $pattern)) { throw 'Could not find HoneyBoard main-editor commitText key dispatch.' }
            $content = [regex]::Replace($content, $pattern, '$1' + "`n" + $hook, 1)
            $spaceCommitHooks++
        }
        if ($descriptor -eq 'Lcom/samsung/android/honeyboard/service/HoneyBoardService;') {
            $content = $content.Replace('invoke-super {p0}, Landroid/inputmethodservice/InputMethodService;->onCreate()V', "invoke-super {p0}, Landroid/inputmethodservice/InputMethodService;->onCreate()V`n    invoke-static {p0}, Lcom/nemotron/voiceime/record/HoneyBoardDialogs;->bind(Landroid/inputmethodservice/InputMethodService;)V")
        }
        if ($descriptor -eq 'Lcom/samsung/android/honeyboard/base/rts/ftu/AbsAgreementDialog;') {
            $content = [regex]::Replace($content, '(invoke-virtual \{(\w+), \w+\}, Landroid/view/Window;->setType\(I\)V)', '$1' + "`n    invoke-static {" + '$2' + '}, Lcom/nemotron/voiceime/record/HoneyBoardDialogs;->attach(Landroid/view/Window;)V')
        }
        [IO.File]::WriteAllText($target, $content, [Text.UTF8Encoding]::new($false))
        [void]$existingClasses.Add($descriptor)
        $insertedClassCount++
    }
}
if ($dictationHooks -ne 1) { throw "Expected one HoneyBoard double-space dictation hook, found $dictationHooks" }
if ($spaceCommitHooks -ne 1) { throw "Expected one HoneyBoard semantic space-key hook, found $spaceCommitHooks" }

# Keep HoneyBoard's coroutine ServiceLoader configuration isolated as well.
$hbServices = Join-Path $hb 'unknown\META-INF\services'
if (Test-Path -LiteralPath $hbServices) {
    foreach ($serviceFile in Get-ChildItem -LiteralPath $hbServices -File -Filter 'kotlinx.coroutines.*') {
        $serviceName = 'com.nemotron.honeyboard.shaded.' + $serviceFile.Name
        $serviceTarget = Join-Path $base ('unknown\META-INF\services\' + $serviceName)
        New-Item -ItemType Directory -Path (Split-Path -Parent $serviceTarget) -Force | Out-Null
        $serviceText = [IO.File]::ReadAllText($serviceFile.FullName).Replace('kotlinx.coroutines.', 'com.nemotron.honeyboard.shaded.kotlinx.coroutines.')
        [IO.File]::WriteAllText($serviceTarget, $serviceText)
    }
}

# Carry Samsung's dictionaries and native engines into the same APK.
foreach ($folder in @('assets', 'lib')) {
    $from = Join-Path $hb $folder
    $to = Join-Path $base $folder
    if (-not (Test-Path -LiteralPath $from)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $from -Recurse -File) {
        $rel = $file.FullName.Substring($from.Length + 1)
        $target = Join-Path $to $rel
        if (Test-Path -LiteralPath $target) { throw "Unexpected asset/library collision: $rel" }
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
    }
}

# Preserve all Nemotron components and add HoneyBoard's full services/settings.
$androidNs = 'http://schemas.android.com/apk/res/android'
$nemManifestPath = Join-Path $base 'AndroidManifest.xml'
[xml] $nemManifest = Get-Content -LiteralPath $nemManifestPath -Raw
[xml] $hbManifest = Get-Content -LiteralPath (Join-Path $hb 'AndroidManifest.xml') -Raw
$nemRoot = $nemManifest.manifest
$hbRoot = $hbManifest.manifest
# Samsung Keyboard 5.9.30 targets API 33 and relies on legacy visibility of
# enabled_input_methods. Keep the combined app at API 33 for its receivers.
$usesSdk = $nemRoot.SelectSingleNode('uses-sdk')
if ($usesSdk) { $usesSdk.SetAttribute('targetSdkVersion', $androidNs, '33') }
$apktoolConfig = Join-Path $base 'apktool.yml'
$configText = [IO.File]::ReadAllText($apktoolConfig)
$configText = $configText -replace '(?m)^  targetSdkVersion:.*$', '  targetSdkVersion: 33'
$configText = $configText -replace '(?m)^doNotCompress:\r?$', "doNotCompress:`n- ogg`n- wav"
[IO.File]::WriteAllText($apktoolConfig, $configText)
function Rewrite-HoneyBoardPackage([System.Xml.XmlNode] $node) {
    $elements = @($node)
    foreach ($element in $node.SelectNodes('.//*')) { $elements += $element }
    foreach ($element in $elements) {
        foreach ($attribute in @($element.Attributes)) {
            $attribute.Value = $attribute.Value.Replace($oldHbPackage, $newAppPackage)
            $attribute.Value = $attribute.Value.Replace('androidx.', 'com.nemotron.honeyboard.shaded.androidx.')
            $attribute.Value = $attribute.Value.Replace('com.google.android.material.', 'com.nemotron.honeyboard.shaded.com.google.android.material.')
            $attribute.Value = [regex]::Replace($attribute.Value, '@(?:\+)?[A-Za-z_]+/[A-Za-z0-9_.$]+', {
                param($m)
                $resourceKey = $m.Value.TrimStart('@', '+')
                if ($resourceRenames.ContainsKey($resourceKey)) { return '@' + $resourceRenames[$resourceKey] }
                return $m.Value
            })
            # The side-by-side donor APK suffixes many provider authorities with
            # .patched. Avoid colliding with that donor if it is still installed.
            if ($attribute.LocalName -eq 'authorities' -and $attribute.Value.EndsWith('.patched') -and -not $attribute.Value.StartsWith($newAppPackage)) {
                $attribute.Value = $attribute.Value.Substring(0, $attribute.Value.Length - '.patched'.Length) + '.nemotron'
            }
        }
    }
}
$hbQueries = $hbRoot.SelectSingleNode('queries')
if ($hbQueries) {
    $nemQueries = $nemRoot.SelectSingleNode('queries')
    if (-not $nemQueries) {
        $nemQueries = $nemManifest.CreateElement('queries')
        [void]$nemRoot.AppendChild($nemQueries)
    }
    foreach ($query in $hbQueries.ChildNodes) {
        if ($query.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
        $copy = $nemManifest.ImportNode($query, $true)
        Rewrite-HoneyBoardPackage $copy
        [void]$nemQueries.AppendChild($copy)
    }
}
foreach ($tag in @('uses-permission', 'uses-permission-sdk-23', 'permission', 'permission-group', 'permission-tree', 'uses-feature')) {
    foreach ($sourceNode in $hbRoot.SelectNodes($tag)) {
        $copy = $nemManifest.ImportNode($sourceNode, $true)
        Rewrite-HoneyBoardPackage $copy
        $name = $copy.GetAttribute('name', $androidNs)
        $present = $false
        foreach ($current in $nemRoot.SelectNodes($tag)) {
            if ($current.GetAttribute('name', $androidNs) -eq $name -and $name) { $present = $true; break }
        }
        if (-not $present) { [void]$nemRoot.AppendChild($copy) }
    }
}
$nemApp = $nemRoot.application
$hbApp = $hbRoot.application
$nemTheme = $nemApp.GetAttribute('theme', $androidNs)
foreach ($activity in $nemApp.SelectNodes('activity')) {
    if ($nemTheme -and -not $activity.HasAttribute('theme', $androidNs)) {
        [void]$activity.SetAttribute('theme', $androidNs, $nemTheme)
    }
}
$hbTheme = $hbApp.GetAttribute('theme', $androidNs)
if ($hbTheme) { [void]$nemApp.SetAttribute('theme', $androidNs, $hbTheme) }
$nemApp.SetAttribute('largeHeap', $androidNs, 'true')
$nemApp.SetAttribute('extractNativeLibs', $androidNs, 'true')
$componentTags = @('activity', 'activity-alias', 'service', 'receiver', 'provider', 'meta-data', 'uses-library', 'property')
foreach ($component in $hbApp.ChildNodes) {
    if ($component.NodeType -ne [System.Xml.XmlNodeType]::Element -or $componentTags -notcontains $component.LocalName) { continue }
    $componentName = $component.GetAttribute('name', $androidNs)
    $componentName = $componentName.Replace('androidx.', 'com.nemotron.honeyboard.shaded.androidx.')
    $componentName = $componentName.Replace('com.google.android.material.', 'com.nemotron.honeyboard.shaded.com.google.android.material.')
    if ($component.LocalName -eq 'activity' -and $componentName -match '\.SpeakKeyboardInputAloudShortcut$') {
        $isAccessibilityShortcut = $false
        foreach ($category in $component.SelectNodes('.//category')) {
            if ($category.GetAttribute('name', $androidNs) -eq 'android.intent.category.ACCESSIBILITY_SHORTCUT_TARGET') {
                $isAccessibilityShortcut = $true
                break
            }
        }
        # HoneyBoard's cloned shortcut duplicates the one already provided by
        # Samsung Keyboard and makes the merged IME appear as another
        # accessibility feature to banking apps.
        if ($isAccessibilityShortcut) { continue }
    }
    if ($component.LocalName -eq 'service' -and $componentName -eq 'com.samsung.android.honeyboard.nemotron.NemotronDictationService') { continue }
    if ($component.LocalName -eq 'service' -and $componentName -eq 'com.samsung.android.writingtoolkit.service.FakeHoneyBoardService') { continue }
    $existing = $null
    if ($componentName) {
        foreach ($candidate in $nemApp.ChildNodes) {
            if ($candidate.LocalName -eq $component.LocalName -and $candidate.GetAttribute('name', $androidNs) -eq $componentName) { $existing = $candidate; break }
        }
    }
    if (-not $existing) {
        $copy = $nemManifest.ImportNode($component, $true)
        Rewrite-HoneyBoardPackage $copy
        if ($component.LocalName -eq 'service' -and $componentName -eq 'com.samsung.android.honeyboard.service.HoneyBoardService') {
            $copy.SetAttribute('label', $androidNs, 'Samsung Keyboard')
        }
        [void]$nemApp.AppendChild($copy)
        continue
    }
    foreach ($child in $component.ChildNodes) {
        if ($child.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
        $childName = $child.GetAttribute('name', $androidNs)
        $seen = $false
        if ($childName) {
            foreach ($present in $existing.ChildNodes) {
                if ($present.LocalName -eq $child.LocalName -and $present.GetAttribute('name', $androidNs) -eq $childName) { $seen = $true; break }
            }
        } else {
            foreach ($present in $existing.ChildNodes) {
                if ($present.OuterXml -eq $child.OuterXml) { $seen = $true; break }
            }
        }
        if (-not $seen) {
            $copyChild = $nemManifest.ImportNode($child, $true)
            Rewrite-HoneyBoardPackage $copyChild
            [void]$existing.AppendChild($copyChild)
        }
    }
}
$nemManifest.Save($nemManifestPath)

# Connect the Nemotron application lifecycle to the original Samsung application
# class. Both lifecycles run through the inheritance chain.
$appSmali = Get-ChildItem -LiteralPath $base -Recurse -Filter 'NemotronApp.smali' | Select-Object -First 1
if (-not $appSmali) { throw 'NemotronApp class was not found in decoded DEX.' }
$appCode = [IO.File]::ReadAllText($appSmali.FullName)
$appCode = $appCode.Replace('.super Landroid/app/Application;', '.super Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;')
$appCode = $appCode.Replace('Landroid/app/Application;-><init>()V', 'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;-><init>()V')
$appCode = $appCode.Replace('Landroid/app/Application;->onCreate()V', 'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;->onCreate()V')
[IO.File]::WriteAllText($appSmali.FullName, $appCode, [Text.UTF8Encoding]::new($false))

Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'HoneyBoardDialogs.smali') -Destination (Join-Path $base 'smali_classes5\com\nemotron\voiceime\record\HoneyBoardDialogs.smali')
Write-Host "Injected $insertedClassCount HoneyBoard classes; reused $duplicateClassCount shared library classes."
& $JavaExe -jar $ApktoolJar b $base -o $unsigned
if ($LASTEXITCODE -ne 0) { throw "APKTool rebuild failed. Working files are at $work" }
& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed. Working files are at $work" }
& $apksigner sign --ks $debugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk $aligned
if ($LASTEXITCODE -ne 0) { throw "APK signing failed. Working files are at $work" }
& $apksigner verify --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw "Final APK signature verification failed. Working files are at $work" }
Write-Host "Unified Nemotron + Samsung Keyboard APK: $OutputApk"
Write-Host "Intermediate files: $work"
