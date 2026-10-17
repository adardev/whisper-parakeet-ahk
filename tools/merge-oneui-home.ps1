param(
    [Parameter(Mandatory = $true)] [string] $OneUIHomeApk,
    [Parameter(Mandatory = $true)] [string] $NemotronApk,
    [Parameter(Mandatory = $true)] [string] $OutputApk,
    [Parameter(Mandatory = $true)] [string] $ApktoolJar,
    [string] $AndroidSdk = "$env:ANDROID_HOME",
    [string] $JavaExe = "$env:JAVA_HOME\bin\java.exe",
    [Parameter(Mandatory = $true)] [string] $FrameworkDir,
    [string] $DecodedWorkDir
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JavaExe)

foreach ($file in @($OneUIHomeApk, $NemotronApk, $ApktoolJar, $JavaExe)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Required file was not found: $file"
    }
}
if (-not (Test-Path -LiteralPath $FrameworkDir -PathType Container)) { throw "Framework directory not found: $FrameworkDir" }
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
if ($DecodedWorkDir) {
    $work = (Resolve-Path -LiteralPath $DecodedWorkDir).Path
    $base = Join-Path $work 'nemotron'
    $hb = Join-Path $work 'honeyboard'
    if (-not (Test-Path -LiteralPath (Join-Path $base 'AndroidManifest.xml')) -or -not (Test-Path -LiteralPath (Join-Path $hb 'AndroidManifest.xml'))) {
        throw "Decoded work directory must contain nemotron and honeyboard APKtool trees: $work"
    }
} else {
    $work = Join-Path $outDir ('.nemotron-keyboard-build-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $work | Out-Null
    $base = Join-Path $work 'nemotron'
    $hb = Join-Path $work 'honeyboard'
}
$unsigned = Join-Path $work 'combined-unsigned.apk'
$aligned = Join-Path $work 'combined-aligned.apk'

if (-not $DecodedWorkDir) {
    & $JavaExe -jar $ApktoolJar d $NemotronApk -p $FrameworkDir -t A54 -o $base -f
    if ($LASTEXITCODE -ne 0) { throw 'Could not decode the Nemotron APK.' }
    & $JavaExe -jar $ApktoolJar d $OneUIHomeApk -p $FrameworkDir -t A54 -o $hb -f
    if ($LASTEXITCODE -ne 0) { throw 'Could not decode the One UI Home APK.' }
}

# Keep Nemotron's resource IDs (and app data) stable. Read complete compiled
# resource tables, including non-public resources, and namespace collisions.
# All HoneyBoard attributes receive new IDs in their original order: its compiled
# styleable arrays must stay sorted or ConstraintLayout and themes break.
$nemPublicPath = Join-Path $base 'res\values\public.xml'
[xml] $nemPublic = Get-Content -LiteralPath $nemPublicPath -Raw
$nemTable = @(& $aapt dump resources $NemotronApk)
if ($LASTEXITCODE -ne 0) { throw 'Could not read the Nemotron resource table.' }
$hbTable = @(& $aapt dump resources $OneUIHomeApk)
if ($LASTEXITCODE -ne 0) { throw 'Could not read the One UI Home resource table.' }
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
        $newResourceName = if ($entry.Name.StartsWith('$')) { '$oneui_' + $entry.Name.Substring(1) } else { 'oneui_' + $entry.Name }
        while ($nemSymbols.ContainsKey("$($entry.Type)/$newResourceName")) {
            $newResourceName = 'oneui_' + $newResourceName
        }
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
$sourceHoneyBoardPackage = 'com.samsung.android.honeyboard'
$newAppPackage = 'com.nemotron.voiceime2'
foreach ($node in $newPublicNodes) { [void]$nemPublic.resources.AppendChild($node) }
$nemPublic.Save($nemPublicPath)

# Copy HoneyBoard resources with matching references to their isolated symbols.
$hbRes = Join-Path $hb 'res'
$nemRes = Join-Path $base 'res'
function Rewrite-HbXmlText([string] $xmlText) {
    # Samsung's easy-mode widget uses an OEM-only category name. Android's
    # public AAPT schema accepts home_screen for the equivalent launcher slot.
    $xmlText = $xmlText.Replace('android:widgetCategory="favorite"', 'android:widgetCategory="home_screen"')
    $xmlText = $xmlText.Replace('androidx.', 'com.nemotron.oneui.shaded.androidx.')
    $xmlText = $xmlText.Replace('com.google.android.material.', 'com.nemotron.oneui.shaded.com.google.android.material.')
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
    $relative = [IO.Path]::GetRelativePath($hbRes, $sourceFile.FullName)
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
        # Keep the cloned launcher's values files separate from the HoneyBoard
        # values files already shipped inside Nemotron. Both use generic names
        # such as arrays.xml and strings.xml.
        $target = Join-Path $targetDir ('oneui-' + $sourceFile.Name)
        $values.Save($target)
        continue
    }

    $resourceDir = Split-Path -Leaf (Split-Path -Parent $sourceFile.FullName)
    $resourceType = ($resourceDir -split '-', 2)[0]
    $resourceName = [IO.Path]::GetFileNameWithoutExtension($sourceFile.Name) -replace '\.9$', ''
    if ($resourceRenames.ContainsKey("$resourceType/$resourceName")) {
        $newFileName = if ($sourceFile.Name.StartsWith('$')) { '$oneui_' + $sourceFile.Name.Substring(1) } else { 'oneui_' + $sourceFile.Name }
        $relative = Join-Path $resourceDir $newFileName
    # The compiled resource table can contain public symbols whose source
    # resource was stripped from the decoded Nemotron APK. Keep the donor
    # resource in that case so AAPT can satisfy the public.xml entry above.
    } elseif ($nemOriginalSymbols.Contains("$resourceType/$resourceName") -and
        (Test-Path -LiteralPath (Join-Path $nemRes $relative) -PathType Leaf)) { continue }
    $target = Join-Path $nemRes $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    if ($sourceFile.Extension -eq '.xml') {
        [IO.File]::WriteAllText($target, (Rewrite-HbXmlText ([IO.File]::ReadAllText($sourceFile.FullName))))
    } else { Copy-Item -LiteralPath $sourceFile.FullName -Destination $target }
}

# Inject HoneyBoard DEX classes and isolate its AndroidX, Material and coroutine
# libraries. Replace the compile-only Application stub with Samsung's class.
$nemSmaliDirs = Get-ChildItem -LiteralPath $base -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' }
$hbSourceManifest = [xml] (Get-Content -LiteralPath (Join-Path $hb 'AndroidManifest.xml') -Raw)
$androidNs = 'http://schemas.android.com/apk/res/android'
$hbProviderAuthorityMap = @{}
foreach ($provider in $hbSourceManifest.SelectNodes('//*[local-name()="provider"]')) {
    $authorities = $provider.GetAttribute('authorities', $androidNs)
    foreach ($authority in ($authorities -split '[;,]')) {
        $authority = $authority.Trim()
        if (-not $authority -or $hbProviderAuthorityMap.ContainsKey($authority)) { continue }
        if ($authority.StartsWith('com.samsung.android.honeyboard', [StringComparison]::Ordinal)) {
            $renamedAuthority = $authority.Replace('com.samsung.android.honeyboard', 'com.nemotron.voiceime2')
        } else {
            $renamedAuthority = $authority + '.nemotron'
        }
        $hbProviderAuthorityMap[$authority] = $renamedAuthority
    }
}
$existingClasses = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($dir in $nemSmaliDirs) {
    foreach ($file in Get-ChildItem -LiteralPath $dir.FullName -Recurse -Filter '*.smali') {
        $classMatch = [regex]::Match([IO.File]::ReadAllText($file.FullName), '(?m)^\.class\s+(?:[^\r\n]*?\s)?(L[^;\s]+;)')
        if ($classMatch.Success) { [void]$existingClasses.Add($classMatch.Groups[1].Value) }
    }
}
$hbSmaliDirs = @(Get-ChildItem -LiteralPath $hb -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' } | Sort-Object { if ($_.Name -eq 'smali') { 1 } else { [int]($_.Name -replace '^smali_classes', '') } })
# Obfuscated Samsung libraries reuse short descriptors for unrelated classes.
# Give every colliding One UI descriptor its own namespace, then rewrite all
# references in One UI DEX files to the isolated copy.
$oneUiRelocationMap = [System.Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach ($hbDir in $hbSmaliDirs) {
    foreach ($file in Get-ChildItem -LiteralPath $hbDir.FullName -Recurse -Filter '*.smali') {
        $content = [IO.File]::ReadAllText($file.FullName)
        $classMatch = [regex]::Match($content, '(?m)^\.class\s+(?:[^\r\n]*?\s)?(L[^;\s]+;)')
        if (-not $classMatch.Success) { continue }
        $sourceDescriptor = $classMatch.Groups[1].Value
        if ($sourceDescriptor -match '^L([^/]+)/' -and $Matches[1] -notin @('android','androidx','com','dagger','java','javax','kotlin','kotlinx','org')) {
            $oneUiRelocationMap[$sourceDescriptor] = 'Lcom/nemotron/oneui/shaded/' + $sourceDescriptor.Substring(1)
            continue
        }
        if ($sourceDescriptor.StartsWith('Lkotlin/')) {
            # One UI Home carries a Kotlin stdlib newer than Nemotron's. Keep
            # its whole stdlib in one package so internal/package access stays
            # valid, even for classes also present in Nemotron.
            $relocated = 'Lcom/nemotron/oneui/shaded/kotlin/' + $sourceDescriptor.Substring(8, $sourceDescriptor.Length - 9) + ';'
            $oneUiRelocationMap[$sourceDescriptor] = $relocated
            continue
        }
        if ($sourceDescriptor.StartsWith('Lkotlinx/coroutines/')) {
            $oneUiRelocationMap[$sourceDescriptor] = $sourceDescriptor.Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/oneui/shaded/kotlinx/coroutines/')
            continue
        }
        $descriptor = $sourceDescriptor
        $descriptor = $descriptor.Replace('Landroidx/', 'Lcom/nemotron/oneui/shaded/androidx/')
        $descriptor = $descriptor.Replace('Lcom/google/android/material/', 'Lcom/nemotron/oneui/shaded/com/google/android/material/')
        if ($existingClasses.Contains($descriptor)) {
            $relocated = 'Lcom/nemotron/oneui/shaded/' + $descriptor.Substring(1, $descriptor.Length - 2) + ';'
            $oneUiRelocationMap[$descriptor] = $relocated
        }
    }
}
# Some One UI releases rely on Kotlin stdlib classes supplied by their platform
# image instead of packaging them. Map Nemotron's existing stdlib classes to
# the clone namespace as fallbacks, while preserving donor versions above.
foreach ($baseSmaliDir in $nemSmaliDirs) {
    foreach ($file in Get-ChildItem -LiteralPath $baseSmaliDir.FullName -Recurse -Filter '*.smali') {
        $rel = $file.FullName.Substring($baseSmaliDir.FullName.Length + 1).Replace('\', '/')
        if (-not $rel.StartsWith('kotlin/', [StringComparison]::Ordinal) -and
            -not $rel.StartsWith('kotlinx/coroutines/', [StringComparison]::Ordinal)) { continue }
        $descriptor = 'L' + $rel.Substring(0, $rel.Length - 6) + ';'
        if (-not $oneUiRelocationMap.ContainsKey($descriptor)) {
            $oneUiRelocationMap[$descriptor] = $descriptor.Replace('Lkotlin/', 'Lcom/nemotron/oneui/shaded/kotlin/').Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/oneui/shaded/kotlinx/coroutines/')
        }
    }
}
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
        $content = [IO.File]::ReadAllText($file.FullName)
        $classMatch = [regex]::Match($content, '(?m)^\.class\s+(?:[^\r\n]*?\s)?(L[^;\s]+;)')
        if (-not $classMatch.Success) { continue }
        $originalDescriptor = $classMatch.Groups[1].Value
        $sourceDescriptor = $originalDescriptor
        if (-not $sourceDescriptor.StartsWith('Lkotlin/')) {
            $originalDescriptor = $sourceDescriptor.Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/honeyboard/shaded/kotlinx/coroutines/')
        }
        if (-not $sourceDescriptor.StartsWith('Lkotlin/')) {
            $originalDescriptor = $originalDescriptor.Replace('Landroidx/', 'Lcom/nemotron/oneui/shaded/androidx/')
            $originalDescriptor = $originalDescriptor.Replace('Lcom/google/android/material/', 'Lcom/nemotron/oneui/shaded/com/google/android/material/')
        }
        if ($rel.Replace('\', '/').StartsWith('kotlinx/coroutines/')) {
            $rel = 'com\nemotron\oneui\shaded\' + $rel
        } elseif ($rel.Replace('\', '/').StartsWith('androidx/') -or $rel.Replace('\', '/').StartsWith('com/google/android/material/')) {
            $rel = 'com\nemotron\oneui\shaded\' + $rel
        }
        $descriptor = $originalDescriptor
        $mapKey = if ($sourceDescriptor.StartsWith('Lkotlin/')) { $sourceDescriptor } else { $originalDescriptor }
        if ($sourceDescriptor.StartsWith('Lkotlin/')) {
            $descriptor = 'Lcom/nemotron/oneui/shaded/' + $sourceDescriptor.Substring(1, $sourceDescriptor.Length - 2) + ';'
            $rel = $descriptor.Substring(1, $descriptor.Length - 2).Replace('/', '\') + '.smali'
        } elseif ($sourceDescriptor.StartsWith('Lkotlinx/coroutines/')) {
            $descriptor = $sourceDescriptor.Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/oneui/shaded/kotlinx/coroutines/')
            $rel = $descriptor.Substring(1, $descriptor.Length - 2).Replace('/', '\') + '.smali'
        } elseif ($oneUiRelocationMap.ContainsKey($mapKey)) {
            $descriptor = $oneUiRelocationMap[$mapKey]
            $rel = $descriptor.Substring(1, $descriptor.Length - 2).Replace('/', '\') + '.smali'
        }
        if ($descriptor.StartsWith('Lcom/samsung/android/honeyboard/nemotron/NemotronDictationService')) { continue }
        $target = Join-Path $destDir $rel
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        $content = $content.Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/oneui/shaded/kotlinx/coroutines/')
        $content = $content.Replace('kotlinx.coroutines.', 'com.nemotron.oneui.shaded.kotlinx.coroutines.')
        $content = $content.Replace('Lkotlin/', 'Lcom/nemotron/oneui/shaded/kotlin/')
        $content = $content.Replace('kotlin.', 'com.nemotron.oneui.shaded.kotlin.')
        $content = $content.Replace('Landroidx/', 'Lcom/nemotron/oneui/shaded/androidx/')
        $content = $content.Replace('androidx.', 'com.nemotron.oneui.shaded.androidx.')
        $content = $content.Replace('Lcom/google/android/material/', 'Lcom/nemotron/oneui/shaded/com/google/android/material/')
        $content = $content.Replace('com.google.android.material.', 'com.nemotron.oneui.shaded.com.google.android.material.')
        $content = [regex]::Replace($content, 'invoke-virtual(/range)? (\{[^}]+\}), Landroid/content/Context;->getDisplay\(\)Landroid/view/Display;', 'invoke-static$1 $2, Lcom/nemotron/oneui/compat/LauncherCompat;->getDisplay(Landroid/content/Context;)Landroid/view/Display;')
        $content = [regex]::Replace($content, 'invoke-virtual(/range)? (\{[^}]+\}), Landroid/content/Context;->createWindowContext\(ILandroid/os/Bundle;\)Landroid/content/Context;', 'invoke-static$1 $2, Lcom/nemotron/oneui/compat/LauncherCompat;->createWindowContext(Landroid/content/Context;ILandroid/os/Bundle;)Landroid/content/Context;')
        $content = [regex]::Replace($content, 'L[A-Za-z0-9_$/-]+;', {
            param($match)
            if ($oneUiRelocationMap.ContainsKey($match.Value)) { return $oneUiRelocationMap[$match.Value] }
            return $match.Value
        })
        $content = [regex]::Replace($content, '(?<=\")([A-Za-z_$][A-Za-z0-9_.$]*(?:\.[A-Za-z0-9_$]+)+)(?=\")', {
            param($match)
            $classDescriptor = 'L' + $match.Value.Replace('.', '/') + ';'
            if ($oneUiRelocationMap.ContainsKey($classDescriptor)) {
                return $oneUiRelocationMap[$classDescriptor].Substring(1, $oneUiRelocationMap[$classDescriptor].Length - 2).Replace('/', '.')
            }
            return $match.Value
        })
        if ($originalDescriptor -eq 'Lcom/honeyspace/ui/honeypots/workspace/viewmodel/WorkspaceViewModel;') {
            $filter = @'
    invoke-virtual {p1}, Lcom/honeyspace/sdk/source/entity/PackageOperation$Added;->getPackageName()Ljava/lang/String;
    move-result-object v0
    const-string v1, "com.instagram.android"
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_skip_home_app
    const-string v1, "com.whatsapp"
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_skip_home_app
    goto :nemotron_continue_home_app
    :nemotron_skip_home_app
    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
    return-object v0
    :nemotron_continue_home_app
'@
            $content = [regex]::Replace($content, '(?m)(\.method public final v0\(Lcom/honeyspace/sdk/source/entity/PackageOperation\$Added;L[^;]*ContinuationImpl;\)Ljava/lang/Object;\r?\n\s*\.locals 8\r?\n)', { param($m) $m.Value + $filter }, 1)
        }
        if ($originalDescriptor -eq 'Lcom/sec/android/app/launcher/LauncherApplication;') {
            $content = $content -replace '(?m)^\.class public final Lcom/sec/android/app/launcher/LauncherApplication;', '.class public Lcom/sec/android/app/launcher/LauncherApplication;'
            $content = $content.Replace('.method public final onCreate()V', '.method public onCreate()V')
            $content = $content.Replace('.method public final onTerminate()V', '.method public onTerminate()V')
            $content = $content.Replace('.method public final onConfigurationChanged(Landroid/content/res/Configuration;)V', '.method public onConfigurationChanged(Landroid/content/res/Configuration;)V')
        }
        if ($originalDescriptor -eq 'Lcom/samsung/android/honeyboard/app/HoneyBoardApplication;') {
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
        $content = $content.Replace(
            'Landroid/provider/Settings$Secure;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I',
            'Lcom/nemotron/voiceime/record/HoneyBoardCompat;->getSecureInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I')
        $content = $content.Replace(
            'invoke-virtual {p0, p1, v2}, Landroid/content/Context;->sendBroadcastAsUser(Landroid/content/Intent;Landroid/os/UserHandle;)V',
            'invoke-virtual {p0, p1}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V')
        $content = [regex]::Replace($content, '(?m)^(\s*)const/high16 (\w+), (0x7f[0-9a-fA-F]{6})\b', {
            param($match)
            $value = [Convert]::ToUInt32($match.Groups[3].Value.Substring(2), 16)
            if (($value -band 0xffff) -ne 0) { return $match.Groups[1].Value + 'const ' + $match.Groups[2].Value + ', ' + $match.Groups[3].Value }
            return $match.Value
        })
        foreach ($authority in $hbProviderAuthorityMap.Keys) {
            $content = $content.Replace($authority, $hbProviderAuthorityMap[$authority])
        }
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
        if ($descriptor -eq 'Lcom/samsung/android/honeyboard/service/HoneyBoardService;') {
            $content = $content.Replace('invoke-super {p0}, Landroid/inputmethodservice/InputMethodService;->onCreate()V', "invoke-super {p0}, Landroid/inputmethodservice/InputMethodService;->onCreate()V`n    invoke-static {p0}, Lcom/nemotron/voiceime/record/VoiceRecordService;->bindHoneyBoardService(Landroid/content/Context;)V`n    invoke-static {p0}, Lcom/nemotron/voiceime/record/HoneyBoardDialogs;->bind(Landroid/inputmethodservice/InputMethodService;)V")
        }
        if ($descriptor -eq 'Lcom/samsung/android/honeyboard/base/rts/ftu/AbsAgreementDialog;') {
            $content = [regex]::Replace($content, '(invoke-virtual \{(\w+), \w+\}, Landroid/view/Window;->setType\(I\)V)', '$1' + "`n    invoke-static {" + '$2' + '}, Lcom/nemotron/voiceime/record/HoneyBoardDialogs;->attach(Landroid/view/Window;)V')
        }
        [IO.File]::WriteAllText($target, $content, [Text.UTF8Encoding]::new($false))
        [void]$existingClasses.Add($descriptor)
        $insertedClassCount++
    }
}

# The donor APK omits some Kotlin stdlib implementation classes and expects
# them from the system/classpath. Copy only the missing classes from Nemotron's
# stdlib into the same isolated namespace, with all self references relocated.
$maxDex++
$kotlinFallbackDir = Join-Path $base "smali_classes$maxDex"
New-Item -ItemType Directory -Path $kotlinFallbackDir | Out-Null
$kotlinFallbackCount = 0
foreach ($baseSmaliDir in $nemSmaliDirs) {
    foreach ($file in Get-ChildItem -LiteralPath $baseSmaliDir.FullName -Recurse -Filter '*.smali') {
        $rel = $file.FullName.Substring($baseSmaliDir.FullName.Length + 1).Replace('/', '\')
        if (-not $rel.StartsWith('kotlin\', [StringComparison]::Ordinal) -and -not $rel.StartsWith('kotlinx\coroutines\', [StringComparison]::Ordinal)) { continue }
        $targetRel = if ($rel.StartsWith('kotlin\', [StringComparison]::Ordinal)) {
            'com\nemotron\oneui\shaded\' + $rel
        } else {
            $rel.Replace('kotlinx\coroutines\', 'com\nemotron\oneui\shaded\kotlinx\coroutines\')
        }
        $alreadyPresent = $false
        foreach ($dexDir in Get-ChildItem -LiteralPath $base -Directory | Where-Object { $_.Name -match '^smali(?:_classes\d+)?$' }) {
            if (Test-Path -LiteralPath (Join-Path $dexDir.FullName $targetRel)) { $alreadyPresent = $true; break }
        }
        if ($alreadyPresent) { continue }
        $target = Join-Path $kotlinFallbackDir $targetRel
        $content = [IO.File]::ReadAllText($file.FullName)
        $content = $content.Replace('Lkotlin/', 'Lcom/nemotron/oneui/shaded/kotlin/')
        $content = $content.Replace('kotlin.', 'com.nemotron.oneui.shaded.kotlin.')
        $content = $content.Replace('Lkotlinx/coroutines/', 'Lcom/nemotron/oneui/shaded/kotlinx/coroutines/')
        $content = $content.Replace('kotlinx.coroutines.', 'com.nemotron.oneui.shaded.kotlinx.coroutines.')
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        [IO.File]::WriteAllText($target, $content, [Text.UTF8Encoding]::new($false))
        $kotlinFallbackCount++
    }
}

# Keep HoneyBoard's coroutine ServiceLoader configuration isolated as well.
$hbServices = Join-Path $hb 'unknown\META-INF\services'
if (Test-Path -LiteralPath $hbServices) {
    foreach ($serviceFile in Get-ChildItem -LiteralPath $hbServices -File -Filter 'kotlinx.coroutines.*') {
        $serviceName = 'com.nemotron.oneui.shaded.' + $serviceFile.Name
        $serviceTarget = Join-Path $base ('unknown\META-INF\services\' + $serviceName)
        New-Item -ItemType Directory -Path (Split-Path -Parent $serviceTarget) -Force | Out-Null
        $serviceText = [IO.File]::ReadAllText($serviceFile.FullName).Replace('kotlinx.coroutines.', 'com.nemotron.oneui.shaded.kotlinx.coroutines.')
        [IO.File]::WriteAllText($serviceTarget, $serviceText)
    }
}

# Carry Samsung's dictionaries and native engines into the same APK.
foreach ($folder in @('assets', 'lib')) {
    $from = Join-Path $hb $folder
    $to = Join-Path $base $folder
    if (-not (Test-Path -LiteralPath $from)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $from -Recurse -File) {
        $rel = [IO.Path]::GetRelativePath($from, $file.FullName)
        $target = Join-Path $to $rel
        if (Test-Path -LiteralPath $target) {
            $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
            if ($sourceHash -eq $targetHash -or $rel -eq 'NOTICE' -or $rel.StartsWith('dexopt\')) { continue }
            if ($rel -match '^SettingsBnRDTD_.*_OS\.xml$') { Copy-Item -LiteralPath $file.FullName -Destination $target -Force; continue }
            throw "Unexpected asset/library collision with different contents: $rel"
        }
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
$hbPermissionNameMap = @{}
foreach ($permission in $hbRoot.SelectNodes('permission')) {
    $permissionName = $permission.GetAttribute('name', $androidNs)
    if ($permissionName) {
        # A transplanted IME must not claim permissions still owned by the
        # installed Samsung package (some declarations use Bixby/Docomo names).
        $safePermissionName = $permissionName -replace '[^A-Za-z0-9_.]', '_'
        $hbPermissionNameMap[$permissionName] = $newAppPackage + '.honeyboard.permission.' + $safePermissionName
    }
}
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
            if ($attribute.LocalName -eq 'authorities') {
                foreach ($authority in $hbProviderAuthorityMap.Keys) {
                    $attribute.Value = $attribute.Value.Replace($authority, $hbProviderAuthorityMap[$authority])
                }
            }
            if ($hbPermissionNameMap.ContainsKey($attribute.Value)) {
                $attribute.Value = $hbPermissionNameMap[$attribute.Value]
            }
            $attribute.Value = $attribute.Value.Replace('androidx.', 'com.nemotron.oneui.shaded.androidx.')
            $attribute.Value = $attribute.Value.Replace('com.google.android.material.', 'com.nemotron.oneui.shaded.com.google.android.material.')
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
    $componentName = $componentName.Replace('androidx.', 'com.nemotron.oneui.shaded.androidx.')
    $componentName = $componentName.Replace('com.google.android.material.', 'com.nemotron.oneui.shaded.com.google.android.material.')
    if ($componentName -and -not $componentName.StartsWith('.')) {
        $componentDescriptor = 'L' + $componentName.Replace('.', '/') + ';'
        if ($oneUiRelocationMap.ContainsKey($componentDescriptor)) {
            $componentName = $oneUiRelocationMap[$componentDescriptor].Substring(1, $oneUiRelocationMap[$componentDescriptor].Length - 2).Replace('/', '.')
        }
    }
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
        if ($componentName) { $copy.SetAttribute('name', $androidNs, $componentName) }
        if ($component.LocalName -eq 'activity-alias' -and $componentName -eq 'com.sec.android.app.launcher.activities.LauncherActivity') {
            $copy.SetAttribute('label', $androidNs, 'Nemotron')
        }
        if ($component.LocalName -eq 'service' -and $componentName -eq 'com.samsung.android.honeyboard.service.HoneyBoardService') {
            $copy.SetAttribute('label', $androidNs, 'Nemotron Keyboard')
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

# Add One UI Home's Hilt Application to the existing Nemotron -> HoneyBoard chain.
$hbApp = Get-ChildItem -LiteralPath $base -Recurse -Filter 'HoneyBoardApplication.smali' | Select-Object -First 1
if (-not $hbApp) { throw 'HoneyBoardApplication class was not found.' }
$hbCode = [IO.File]::ReadAllText($hbApp.FullName)
$hbCode = $hbCode.Replace('.super Landroid/app/Application;', '.super Lcom/sec/android/app/launcher/LauncherApplication;')
$hbCode = $hbCode.Replace('Landroid/app/Application;-><init>()V', 'Lcom/sec/android/app/launcher/LauncherApplication;-><init>()V')
$hbCode = $hbCode.Replace('Landroid/app/Application;->onCreate()V', 'Lcom/sec/android/app/launcher/LauncherApplication;->onCreate()V')
$hbCode = $hbCode.Replace('Landroid/app/Application;->onTerminate()V', 'Lcom/sec/android/app/launcher/LauncherApplication;->onTerminate()V')
[IO.File]::WriteAllText($hbApp.FullName, $hbCode, [Text.UTF8Encoding]::new($false))
Write-Host "Injected $insertedClassCount One UI Home classes; reused $duplicateClassCount shared library classes; added $kotlinFallbackCount Kotlin fallback classes."
$compatTarget = Join-Path $kotlinFallbackDir 'com\nemotron\oneui\compat\LauncherCompat.smali'
New-Item -ItemType Directory -Path (Split-Path -Parent $compatTarget) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat\LauncherCompat.smali') -Destination $compatTarget
& $JavaExe -jar $ApktoolJar b $base -p $FrameworkDir -o $unsigned
if ($LASTEXITCODE -ne 0) { throw "APKTool rebuild failed. Working files are at $work" }
& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed. Working files are at $work" }
& $apksigner sign --ks $debugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutputApk $aligned
if ($LASTEXITCODE -ne 0) { throw "APK signing failed. Working files are at $work" }
& $apksigner verify --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw "Final APK signature verification failed. Working files are at $work" }
Write-Host "Unified Nemotron + One UI Home + Samsung Keyboard APK: $OutputApk"
Write-Host "Intermediate files: $work"
