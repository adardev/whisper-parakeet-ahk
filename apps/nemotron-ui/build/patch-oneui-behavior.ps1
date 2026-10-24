param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference = 'Stop'
function Edit-Class([string]$Name, [scriptblock]$Edit) {
    $file = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter $Name | Select-Object -First 1
    if (-not $file) { throw "Missing One UI class $Name" }
    $text = [IO.File]::ReadAllText($file.FullName)
    $result = & $Edit $text
    [IO.File]::WriteAllText($file.FullName, $result, [Text.UTF8Encoding]::new($false))
}
# A wrapper shortcut's publisher is Nemotron, but its running task belongs to
# the destination app. Samsung otherwise only matches AppItem/package pairs.
Edit-Class 'CloseTarget$DefaultImpls.smali' {
    param($text)
    if ($text.Contains(':nemotron_original_match')) { return $text }
    [regex]::Replace($text, '(?m)(\.method private static isPackageAndUserMatchedItem\([^\r\n]+\r?\n\s*\.locals \d+\r?\n)', '$1' + @'

    invoke-static {p1, p2}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->matches(Lcom/honeyspace/sdk/source/entity/BaseItem;Lcom/honeyspace/sdk/transition/CloseTarget$Key;)Z
    move-result v0
    if-eqz v0, :nemotron_original_match
    return v0
    :nemotron_original_match
'@)
}
$closeAdapters = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' | Where-Object { $_.Name -in @('A.smali','P0.smali','Z0.smali','r0.1.smali') }
foreach ($adapter in $closeAdapters) {
    $code = [IO.File]::ReadAllText($adapter.FullName)
    $pattern = 'new-instance p0, Lcom/honeyspace/sdk/transition/CloseTarget\$Value;\s+instance-of p1, p1, L[^;]+;\s+invoke-direct \{p0, (\w+), (\w+), p1\}, Lcom/honeyspace/sdk/transition/CloseTarget\$Value;-><init>\(Landroid/view/View;Landroid/content/ComponentName;Z\)V'
    if ($code -match $pattern) {
        $code = [regex]::Replace($code, $pattern, {
            param($m)
            'invoke-static {' + $m.Groups[1].Value + ', ' + $m.Groups[2].Value + ', p1}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->value(Landroid/view/View;Landroid/content/ComponentName;Lcom/honeyspace/sdk/source/entity/ModelItemSupplier;)Lcom/honeyspace/sdk/transition/CloseTarget$Value;' + "`n    move-result-object p0"
        })
        [IO.File]::WriteAllText($adapter.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
}
# Manual addition uses the public pinned-shortcut flow; installation never
# authorizes an automatic native AppItem placement.
$androidNs = 'http://schemas.android.com/apk/res/android'
$settingsPath = Join-Path $DecodedRoot 'res/xml/settings_preferences.xml'
[xml]$settingsXml = Get-Content -LiteralPath $settingsPath -Raw
$manual = $settingsXml.SelectNodes('//*') | Where-Object { $_.GetAttribute('key', $androidNs) -eq 'nemotron_add_apps' } | Select-Object -First 1
if (-not $manual) {
    $manual = $settingsXml.CreateElement('Preference')
    [void]$manual.SetAttribute('key', $androidNs, 'nemotron_add_apps')
    [void]$manual.SetAttribute('title', $androidNs, 'Añadir aplicaciones')
    [void]$manual.SetAttribute('summary', $androidNs, 'Elige qué aplicaciones aparecen en Inicio. Las nuevas no se añaden automáticamente.')
    $intent = $settingsXml.CreateElement('intent')
    [void]$intent.SetAttribute('action', $androidNs, 'android.intent.action.MAIN')
    [void]$intent.SetAttribute('targetPackage', $androidNs, 'com.nemotron.voiceime2')
    [void]$intent.SetAttribute('targetClass', $androidNs, 'com.nemotron.voiceime.ui.ShortcutPickerActivity')
    [void]$manual.AppendChild($intent)
    [void]$settingsXml.DocumentElement.PrependChild($manual)
    $settingsXml.Save($settingsPath)
}
Edit-Class 'ShortcutPickerActivity.smali' {
    param($text)
    $obsoleteLoad = @'
    invoke-static {p0}, Lcom/nemotron/oneui/compat/InstalledApps;->load(Landroid/content/Context;)Ljava/util/List;
    move-result-object v0
    iput-object v0, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;
'@
    $text = $text.Replace($obsoleteLoad.Trim(), '')
    # Kotlin's original onCreate rebuilds the list after super.onCreate. Replace
    # that final assignment so the filtered native list cannot overwrite ours.
    if (-not $text.Contains('# nemotron_manual_picker_list')) {
        $assignment = '    iput-object v2, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;'
        if (-not $text.Contains($assignment)) { throw 'ShortcutPickerActivity app-list assignment was not found.' }
        $replacement = @'
    # nemotron_manual_picker_list
    invoke-static {p0}, Lcom/nemotron/oneui/compat/InstalledApps;->load(Landroid/content/Context;)Ljava/util/List;
    move-result-object v2
    iput-object v2, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;
'@
        $text = $text.Replace($assignment, $replacement.TrimEnd())
    }
    $text = [regex]::Replace($text, '(?ms)^\.method private final requestShortcut\(Lcom/nemotron/voiceime/ui/ShortcutPickerActivity\$AppInfo;\)V.*?^\.end method', @'
.method private final requestShortcut(Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;)V
    .locals 12
    invoke-direct {p0, p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->shortcutDrawable(Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;)Landroid/graphics/drawable/Drawable;
    move-result-object v0
    invoke-direct {p0, v0}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->renderToBitmap(Landroid/graphics/drawable/Drawable;)Landroid/graphics/Bitmap;
    move-result-object v1
    invoke-static {v1}, Landroidx/core/graphics/drawable/IconCompat;->createWithBitmap(Landroid/graphics/Bitmap;)Landroidx/core/graphics/drawable/IconCompat;
    move-result-object v2
    invoke-virtual {p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;->getPkg()Ljava/lang/String;
    move-result-object v3
    new-instance v4, Ljava/lang/StringBuilder;
    const-string v5, "nemotron_app_"
    invoke-direct {v4, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v4
    const-string v5, "_"
    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v4
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v7
    invoke-virtual {v4, v7, v8}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;
    move-result-object v4
    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v4
    new-instance v6, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    invoke-direct {v6, p0, v4}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;-><init>(Landroid/content/Context;Ljava/lang/String;)V
    invoke-virtual {p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;->getLabel()Ljava/lang/String;
    move-result-object v5
    invoke-virtual {v6, v5}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setShortLabel(Ljava/lang/CharSequence;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    invoke-virtual {v6, v5}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setLongLabel(Ljava/lang/CharSequence;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    invoke-virtual {v6, v2}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setIcon(Landroidx/core/graphics/drawable/IconCompat;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    new-instance v9, Landroid/content/Intent;
    const-string v5, "android.intent.action.MAIN"
    invoke-direct {v9, v5}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
    const-string v5, "android.intent.category.LAUNCHER"
    invoke-virtual {v9, v5}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;
    invoke-virtual {p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;->getActivity()Ljava/lang/String;
    move-result-object v5
    new-instance v10, Landroid/content/ComponentName;
    invoke-direct {v10, v3, v5}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v9, v10}, Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;
    const v5, 0x10200000
    invoke-virtual {v9, v5}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;
    invoke-virtual {v6, v9}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setIntent(Landroid/content/Intent;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    invoke-virtual {v6}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->build()Landroidx/core/content/pm/ShortcutInfoCompat;
    move-result-object v11
    const/4 v5, 0x0
    invoke-static {p0, v11, v5}, Landroidx/core/content/pm/ShortcutManagerCompat;->requestPinShortcut(Landroid/content/Context;Landroidx/core/content/pm/ShortcutInfoCompat;Landroid/content/IntentSender;)Z
    move-result v5
    if-eqz v5, :nemotron_pin_failed
    invoke-static {p0, v3}, Lcom/nemotron/oneui/compat/InstalledApps;->selectManually(Landroid/content/Context;Ljava/lang/String;)V
    const-string v4, "Confirma anadir la app a Inicio"
    goto :nemotron_pin_toast
    :nemotron_pin_failed
    const-string v4, "No se pudo solicitar el icono a Inicio"
    :nemotron_pin_toast
    const/4 v5, 0x0
    invoke-static {p0, v4, v5}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v0
    invoke-virtual {v0}, Landroid/widget/Toast;->show()V
    return-void
.end method
'@)
    if (-not $text.Contains('ShortcutManagerCompat;->requestPinShortcut')) {
        throw 'ShortcutPickerActivity must request a real Home pin; refusing to build a toast-only picker.'
    }
    $text
}
$pickerLayout = Join-Path $DecodedRoot 'res/layout/activity_shortcut_picker.xml'
if (Test-Path -LiteralPath $pickerLayout) {
    $pickerText = [IO.File]::ReadAllText($pickerLayout)
    $pickerText = $pickerText.Replace('App shortcuts', 'Añadir aplicaciones').Replace('Press + on an app to pin its shortcut to home with its native icon.', 'Pulsa + para añadir una aplicación a Inicio. Las apps nuevas no se añaden automáticamente.')
    [IO.File]::WriteAllText($pickerLayout, $pickerText, [Text.UTF8Encoding]::new($false))
}
# Apply the persistent policy at the database boundary, including cold startup.
$settingsFragment = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'SettingsFragment.smali' | Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.class public final Lcom/android/homescreen/settings/SettingsFragment;') } | Select-Object -First 1
if (-not $settingsFragment) { throw 'Native Home SettingsFragment missing' }
$settingsCode = [IO.File]::ReadAllText($settingsFragment.FullName)
if (-not $settingsCode.Contains(':nemotron_native_setting')) {
    $settingsCode = [regex]::Replace($settingsCode, '(?m)(\.method public static f\(Lcom/nemotron/oneui/shaded/androidx/preference/Preference;Lcom/android/homescreen/settings/SettingsFragment;\)Lcom/nemotron/oneui/shaded/kotlin/Unit;\r?\n\s*\.locals \d+\r?\n)', '$1' + @'

    move-object/from16 v0, p0
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->getKey()Ljava/lang/String;
    move-result-object v1
    const-string v2, "nemotron_add_apps"
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :nemotron_native_setting
    const/4 v1, 0x1
    invoke-virtual {v0, v1}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->setVisible(Z)V
    new-instance v1, Lcom/nemotron/oneui/compat/AddAppsPreferenceClick;
    invoke-direct {v1}, Lcom/nemotron/oneui/compat/AddAppsPreferenceClick;-><init>()V
    invoke-virtual {v0, v1}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->setOnPreferenceClickListener(Lcom/nemotron/oneui/shaded/androidx/preference/Preference$OnPreferenceClickListener;)V
    sget-object v0, Lcom/nemotron/oneui/shaded/kotlin/Unit;->INSTANCE:Lcom/nemotron/oneui/shaded/kotlin/Unit;
    return-object v0
    :nemotron_native_setting
'@)
    [IO.File]::WriteAllText($settingsFragment.FullName, $settingsCode, [Text.UTF8Encoding]::new($false))
}
# RxJava 3's IO scheduler wakes at its keep-alive interval to evict cached
# workers. Keep normal cleanup, but reduce this idle maintenance from once a
# minute to once every five minutes. Explicit system-property overrides remain
# respected; idle IO workers are retained for up to five minutes instead.
$rxIoKeepAlive = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' |
    Where-Object {
        $code = [IO.File]::ReadAllText($_.FullName)
        $code.Contains('rx3.io-keep-alive-time') -and $code.Contains('Ljava/lang/Long;->getLong')
    } | Select-Object -First 1
if ($rxIoKeepAlive) {
    $rxIoCode = [IO.File]::ReadAllText($rxIoKeepAlive.FullName)
    if (-not $rxIoCode.Contains('nemotron_rx3_io_eviction_300s')) {
        $rxIoPattern = '(?m)(const-string/jumbo v0, "rx3\.io-keep-alive-time"\r?\n\s*const-wide/16 v1, )0x3c(\r?\n\s*invoke-static \{v0, v1, v2\}, Ljava/lang/Long;->getLong)'
        $rxIoMatches = [regex]::Matches($rxIoCode, $rxIoPattern)
        if ($rxIoMatches.Count -ne 1) { throw 'RxJava 3 IO keep-alive default changed; refusing to patch it.' }
        $rxIoCode = [regex]::Replace($rxIoCode, $rxIoPattern, {
            param($match)
            $match.Groups[1].Value + '0x12c # nemotron_rx3_io_eviction_300s' + $match.Groups[2].Value
        }, 1)
        [IO.File]::WriteAllText($rxIoKeepAlive.FullName, $rxIoCode, [Text.UTF8Encoding]::new($false))
    }
} else {
    Write-Verbose 'This One UI Home build has no RxJava 3 IO scheduler to tune.'
}

# RxJava 2 builds also use a global purge executor that scans cancelled tasks.
# Keep cleanup enabled, but run it every five minutes; delayed tasks can remain
# queued for at most 300 s in those builds.
$rxPurge = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' |
    Where-Object {
        $code = [IO.File]::ReadAllText($_.FullName)
        $code.Contains('rx2.purge-period-seconds') -and $code.Contains('Lrv0/k$a;->b:I')
    } | Select-Object -First 1
if ($rxPurge) {
    $rxCode = [IO.File]::ReadAllText($rxPurge.FullName)
    if (-not $rxCode.Contains('nemotron_rx_purge_300s')) {
        $rxPattern = '(?m)(^\s*:cond_1\s*\r?\n)(\s*)(iput v2, p0, Lrv0/k\$a;->b:I)'
        $matches = [regex]::Matches($rxCode, $rxPattern)
        if ($matches.Count -ne 1) { throw 'RxJava purge default layout changed; refusing to patch it.' }
        $rxCode = [regex]::Replace($rxCode, $rxPattern, {
            param($match)
            $newline = if ($match.Value.Contains("`r`n")) { "`r`n" } else { "`n" }
            $match.Groups[1].Value + $match.Groups[2].Value + 'const/16 v2, 0x12c' + $newline +
                $match.Groups[2].Value + $match.Groups[3].Value + ' # nemotron_rx_purge_300s'
        }, 1)
        [IO.File]::WriteAllText($rxPurge.FullName, $rxCode, [Text.UTF8Encoding]::new($false))
    }
} else {
    Write-Verbose 'This One UI Home build has no RxJava2 purge worker to tune.'
}
$delegate = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' | Where-Object { $_.Name -eq 'B.smali' } | Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.field public final synthetic a:Lcom/honeyspace/data/db/SpaceDB_Impl;') } | Select-Object -First 1
if (-not $delegate) { throw 'SpaceDB Room delegate missing' }
$delegateCode = [IO.File]::ReadAllText($delegate.FullName)
if (-not $delegateCode.Contains('Lcom/nemotron/oneui/compat/HomeOnlyPolicy;->apply')) {
    $delegateCode = [regex]::Replace($delegateCode, '(?m)(\.method public final onOpen\([^\r\n]+\r?\n\s*\.locals \d+\r?\n)', '$1' + "`n    invoke-static {p1}, Lcom/nemotron/oneui/compat/HomeOnlyPolicy;->apply(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;)V`n")
    [IO.File]::WriteAllText($delegate.FullName, $delegateCode, [Text.UTF8Encoding]::new($false))
}
$compat = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'LauncherCompat.smali' | Select-Object -First 1
if (-not $compat) { throw 'Launcher compatibility classes missing' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/InstalledApps.smali') -Destination (Join-Path $compat.DirectoryName 'InstalledApps.smali') -Force
$policy = @'
.class public final Lcom/nemotron/oneui/compat/HomeOnlyPolicy;
.super Ljava/lang/Object;
.method public static apply(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;)V
    .locals 4
    const-string v0, "PRAGMA database_list"
    invoke-interface {p0, v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;->prepare(Ljava/lang/String;)Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;
    move-result-object v0
    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->step()Z
    move-result v1
    if-eqz v1, :close
    const/4 v1, 0x2
    invoke-interface {v0, v1}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->getText(I)Ljava/lang/String;
    move-result-object v1
    const-string v2, "HomeOnly.db"
    invoke-virtual {v1, v2}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
    move-result v3
    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->close()V
    if-nez v3, :apply_policy
    const-string v2, "OneUI.db"
    invoke-virtual {v1, v2}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
    move-result v3
    if-eqz v3, :done
    :apply_policy
'@
 $sqlBuffer = [System.Text.StringBuilder]::new()
 $sqlStatements = [System.Collections.Generic.List[string]]::new()
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/home-only-policy.sql')) {
    $line = $line.Trim()
    if (-not $line -or $line.StartsWith('--')) { continue }
    [void]$sqlBuffer.Append($line).Append(' ')
    while (($sqlText = $sqlBuffer.ToString()).Contains(';')) {
        $end = $sqlText.IndexOf(';')
        $sqlStatements.Add($sqlText.Substring(0, $end + 1).Trim())
        [void]$sqlBuffer.Clear()
        [void]$sqlBuffer.Append($sqlText.Substring($end + 1))
    }
}
if ($sqlBuffer.ToString().Trim()) { throw 'Home-only policy SQL contains an unterminated statement.' }
foreach ($sql in $sqlStatements) {
    $escaped = $sql.Replace('\','\\').Replace('"','\"')
    $policy += "`n    const-string v0, `"$escaped`"`n    invoke-static {p0, v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLite;->execSQL(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;Ljava/lang/String;)V`n"
}
$policy += "`n    goto :done`n    :close`n    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->close()V`n    :done`n    return-void`n.end method`n"
[IO.File]::WriteAllText((Join-Path $compat.DirectoryName 'HomeOnlyPolicy.smali'), $policy, [Text.UTF8Encoding]::new($false))
Edit-Class 'SearchBackupReceiver.smali' {
    param($text)
    $text.Replace('const-string v1, "com.sec.android.app.launcher"', 'const-string v1, "com.nemotron.voiceime2"')
}
Edit-Class 'WorkspaceViewModel.smali' {
    param($text)
    $text = [regex]::Replace($text, '(?ms)^\.method public final u\(Lcom/nemotron/oneui/shaded/y8/d0;Z\)V.*?^\.end method', {
        param($m)
        $method = $m.Value
        # Keep automatically discovered apps off Home, but allow an AppItem
        # explicitly chosen in Nemotron's app selector to use One UI's model.
        if (-not $method.Contains(':nemotron_manual_item_v2')) {
        $method = [regex]::Replace($method, '(?m)(^\s*\.locals \d+\r?\n)', '$1' + @'

    move-object/from16 v0, p1
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/y8/d0;->getItem()Lcom/honeyspace/sdk/source/entity/BaseItem;
    move-result-object v0
    instance-of v0, v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    if-eqz v0, :nemotron_manual_item_v2
    move-object/from16 v0, p1
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/y8/d0;->getItem()Lcom/honeyspace/sdk/source/entity/BaseItem;
    move-result-object v0
    check-cast v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/AppItem;->getComponent()Lcom/honeyspace/sdk/source/entity/ComponentKey;
    move-result-object v0
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/ComponentKey;->getPackageName()Ljava/lang/String;
    move-result-object v1
    move-object/from16 v3, p0
    iget-object v2, v3, Lcom/honeyspace/ui/honeypots/workspace/viewmodel/WorkspaceViewModel;->e:Landroid/content/Context;
    invoke-static {v2, v1}, Lcom/nemotron/oneui/compat/InstalledApps;->isManuallySelected(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :nemotron_manual_item_v2
    return-void
    :nemotron_manual_item_v2
'@)
        }
        $method
    })
    [regex]::Replace($text, '(?ms)^\.method public final s\(\)Z.*?^\.end method', @'
.method public final s()Z
    .locals 1
    const/4 v0, 0x0
    return v0
.end method
'@)
}
Edit-Class 'VerticalApplistViewModel.smali' {
    param($text)
    [regex]::Replace($text, '(?ms)^\.method public final f\(Lcom/nemotron/oneui/shaded/h8/d;\)V.*?^\.end method', {
        param($m)
        if ($m.Value.Contains(':nemotron_hide_social_icon')) { return $m.Value }
        [regex]::Replace($m.Value, '(?m)^\s*\.locals 1\s*$', @'
    .locals 2
    invoke-virtual {p1}, Lcom/nemotron/oneui/shaded/h8/d;->d()Lcom/honeyspace/sdk/source/entity/IconItem;
    move-result-object v0
    instance-of v1, v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    if-eqz v1, :nemotron_social_visible
    check-cast v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/AppItem;->getComponent()Lcom/honeyspace/sdk/source/entity/ComponentKey;
    move-result-object v0
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/ComponentKey;->getPackageName()Ljava/lang/String;
    move-result-object v0
    const-string v1, "com.whatsapp"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_hide_social_icon
    const-string v1, "com.instagram.android"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_hide_social_icon
    :nemotron_social_visible
    goto :nemotron_continue_social_filter
    :nemotron_hide_social_icon
    return-void
    :nemotron_continue_social_filter
'@)
    })
}
Edit-Class 'ShellTransitionManager.smali' {
    param($text)
    # Older builds short-circuited One UI's normal pinned-shortcut launch and
    # substituted a scale-up ActivityOptions launch. That bypasses the native
    # launcher transition path; leave the original method body intact.
    $text = [regex]::Replace($text, '(?ms)(\.method public final startShellTransitionForShortcut\([^\r\n]+\r?\n\s*\.locals \d+\s+)invoke-static \{p1, p2\}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->launch\([^\r\n]+\r?\n\s*move-result v0\s+if-eqz v0, :cond_0\s+return-void\s+:cond_0\s+', '$1')
    # Remote task/snapshot transitions require signature permissions. Keep the
    # original launch routing, with Android's ordinary ActivityOptions instead.
    [regex]::Replace($text, '(?ms)^(\.method public final (?:getLaunchOptions|getActivityLaunchOptions)\([^\r\n]+\r?\n).*?^\.end method', {
        param($m)
        $m.Groups[1].Value + @'
    .locals 2
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;
    move-result-object v0
    invoke-static {p1, v0}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->attachCookie(Lcom/honeyspace/sdk/transition/ShellTransition$Info;Landroid/app/ActivityOptions;)V
    new-instance v1, Lcom/honeyspace/transition/utils/ActivityOptionsWrapper;
    invoke-direct {v1, v0}, Lcom/honeyspace/transition/utils/ActivityOptionsWrapper;-><init>(Landroid/app/ActivityOptions;)V
    return-object v1
.end method
'@
    })
}
Edit-Class 'ContextReflection.smali' {
    param($text)
    if ($text.Contains(':own_profile')) { return $text }
    # The personal profile can use the public API; retain the old route for
    # callers explicitly targeting another profile.
    [regex]::Replace($text, '(?m)(\.method public final startActivityAsUser\(Ljava/lang/Object;Landroid/content/Intent;(Landroid/os/Bundle;)?Landroid/os/UserHandle;\)V\r?\n\s*\.locals \d+\r?\n)', {
        param($m)
        $user = if ($m.Groups[2].Success) { 'p4' } else { 'p3' }
        $call = if ($m.Groups[2].Success) { 'invoke-virtual {p1, p2, p3}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V' } else { 'invoke-virtual {p1, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V' }
        $m.Value + "`n    invoke-static {}, Landroid/os/Process;->myUserHandle()Landroid/os/UserHandle;`n    move-result-object v0`n    if-eqz $user, :own_profile`n    invoke-virtual {v0, $user}, Landroid/os/UserHandle;->equals(Ljava/lang/Object;)Z`n    move-result v0`n    if-eqz v0, :other_profile`n    :own_profile`n    check-cast p1, Landroid/content/Context;`n    $call`n    return-void`n    :other_profile`n"
    })
}
Edit-Class 'PackageEventOperator.smali' {
    param($text)
    $text = [regex]::Replace($text, '(?ms)^(\.method public final handlePackageAdded\([^\r\n]+\r?\n).*?^\.end method', {
        param($m)
        $m.Groups[1].Value + "    .locals 1`n    sget-object v0, Lcom/nemotron/oneui/shaded/kotlin/Unit;->INSTANCE:Lcom/nemotron/oneui/shaded/kotlin/Unit;`n    return-object v0`n.end method"
    })
    [regex]::Replace($text, '(?ms)^\.method public final handlePackageChanged\(.*?^\.end method', {
        param($m)
        # extraAddedOperation creates icons when a disabled package returns.
        # Preserve the update/removal callbacks for items already placed.
        $m.Value.Replace('move-object/from16 v14, p4', "new-instance v14, Lcom/nemotron/oneui/compat/NoAutoAdd;`n    invoke-direct {v14}, Lcom/nemotron/oneui/compat/NoAutoAdd;-><init>()V")
    })
}

# The integrated HoneyBoard APK schedules two idle daily jobs for Samsung
# keyboard usage statistics. Nemotron keeps HoneyBoard as its active IME, but
# does not need Samsung's periodic statistics/update tasks. Cancel existing
# jobs at startup and do not enqueue them again.
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
