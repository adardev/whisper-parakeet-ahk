param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference = 'Stop'
function Edit-Class([string]$Name, [scriptblock]$Edit) {
    $file = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter $Name | Select-Object -First 1
    if (-not $file) { throw "Missing One UI class $Name" }
    $text = [IO.File]::ReadAllText($file.FullName)
    $result = & $Edit $text
    [IO.File]::WriteAllText($file.FullName, $result, [Text.UTF8Encoding]::new($false))
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
    if (-not $text.Contains('Lcom/nemotron/oneui/compat/InstalledApps;->freshId')) {
        $text = [regex]::Replace($text, '(?m)^(\s*)invoke-direct \{(\w+), (\w+), (\w+)\}, Landroidx/core/content/pm/ShortcutInfoCompat\$Builder;-><init>\(Landroid/content/Context;Ljava/lang/String;\)V', {
            param($m)
            $idReg = $m.Groups[4].Value
            "    invoke-static {$idReg}, Lcom/nemotron/oneui/compat/InstalledApps;->freshId(Ljava/lang/String;)Ljava/lang/String;`n    move-result-object $idReg`n" + $m.Value
        })
    }
    if ($text.Contains('Lcom/nemotron/oneui/compat/InstalledApps;->load')) { return $text }
    $text.Replace('invoke-super {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->onCreate(Landroid/os/Bundle;)V', @'
invoke-super {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->onCreate(Landroid/os/Bundle;)V
    invoke-static {p0}, Lcom/nemotron/oneui/compat/InstalledApps;->load(Landroid/content/Context;)Ljava/util/List;
    move-result-object v0
    iput-object v0, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;
'@)
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
$delegate = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' | Where-Object { $_.Name -eq 'B.smali' } | Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.field public final synthetic a:Lcom/honeyspace/data/db/SpaceDB_Impl;') } | Select-Object -First 1
if (-not $delegate) { throw 'SpaceDB Room delegate missing' }
$delegateCode = [IO.File]::ReadAllText($delegate.FullName)
if (-not $delegateCode.Contains('Lcom/nemotron/oneui/compat/HomeOnlyPolicy;->apply')) {
    $delegateCode = [regex]::Replace($delegateCode, '(?m)(\.method public final onOpen\([^\r\n]+\r?\n\s*\.locals \d+\r?\n)', '$1' + "`n    invoke-static {p1}, Lcom/nemotron/oneui/compat/HomeOnlyPolicy;->apply(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;)V`n")
    [IO.File]::WriteAllText($delegate.FullName, $delegateCode, [Text.UTF8Encoding]::new($false))
}
$compat = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'LauncherCompat.smali' | Select-Object -First 1
$policy = @'
.class public final Lcom/nemotron/oneui/compat/HomeOnlyPolicy;
.super Ljava/lang/Object;
.method public static apply(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;)V
    .locals 3
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
    move-result v1
    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->close()V
    if-eqz v1, :done
'@
foreach ($sql in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/home-only-policy.sql')) {
    if (-not $sql.Trim() -or $sql.TrimStart().StartsWith('--')) { continue }
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
        if ($m.Value.Contains(':nemotron_explicit_item')) { return $m.Value }
        # HomeOnly's cold-start reconciliation constructs unpersisted AppItems
        # and even pages. Stop those objects before placement and rendering.
        [regex]::Replace($m.Value, '(?m)(^\s*\.locals \d+\r?\n)', '$1' + @'

    move-object/from16 v0, p1
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/y8/d0;->getItem()Lcom/honeyspace/sdk/source/entity/BaseItem;
    move-result-object v0
    instance-of v0, v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    if-eqz v0, :nemotron_explicit_item
    return-void
    :nemotron_explicit_item
'@)
    })
    [regex]::Replace($text, '(?ms)^\.method public final s\(\)Z.*?^\.end method', @'
.method public final s()Z
    .locals 1
    const/4 v0, 0x0
    return v0
.end method
'@)
}
Edit-Class 'ShellTransitionManager.smali' {
    param($text)
    # Remote task/snapshot transitions require signature permissions. Keep the
    # original launch routing, with Android's ordinary ActivityOptions instead.
    [regex]::Replace($text, '(?ms)^(\.method public final (?:getLaunchOptions|getActivityLaunchOptions)\([^\r\n]+\r?\n).*?^\.end method', {
        param($m)
        $m.Groups[1].Value + @'
    .locals 2
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;
    move-result-object v0
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
