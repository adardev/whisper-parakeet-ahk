param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference = 'Stop'
function Edit-Class([string]$Name, [scriptblock]$Edit) {
    $file = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter $Name | Select-Object -First 1
    if (-not $file) { throw "Missing One UI class $Name" }
    $text = [IO.File]::ReadAllText($file.FullName)
    $result = & $Edit $text
    [IO.File]::WriteAllText($file.FullName, $result, [Text.UTF8Encoding]::new($false))
}
# Apply the persistent policy at the database boundary, including cold startup.
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
