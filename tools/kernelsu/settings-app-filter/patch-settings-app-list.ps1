param(
    [Parameter(Mandatory = $true)] [string] $DecodedRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$adapter = Join-Path $DecodedRoot 'smali\com\android\settings\applications\manageapplications\ManageApplications$ApplicationsAdapter.smali'
if (-not (Test-Path -LiteralPath $adapter)) { throw "Settings adapter not found: $adapter" }

$text = [IO.File]::ReadAllText($adapter)
$method = '.method public final onRebuildComplete(Ljava/util/ArrayList;)V'
if (-not $text.Contains($method)) { throw 'Unexpected Settings APK: onRebuildComplete was not found.' }
if ($text.Contains('shouldHideFromInstalledApps')) { throw 'The Settings app-list filter is already present.' }

$filter = @'
    # Hide selected package rows only in the normal Settings > Apps list (list type 0).
    iget-object v6, p0, Lcom/android/settings/applications/manageapplications/ManageApplications$ApplicationsAdapter;->mManageApplications:Lcom/android/settings/applications/manageapplications/ManageApplications;
    iget v6, v6, Lcom/android/settings/applications/manageapplications/ManageApplications;->mListType:I
    if-nez v6, :cond_nemotron_contacts_filter_done
    const/4 v6, 0x0
    :cond_nemotron_contacts_filter_loop
    invoke-virtual {p1}, Ljava/util/ArrayList;->size()I
    move-result v7
    if-ge v6, v7, :cond_nemotron_contacts_filter_done
    invoke-virtual {p1, v6}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
    move-result-object v8
    check-cast v8, Lcom/android/settingslib/applications/ApplicationsState$AppEntry;
    iget-object v9, v8, Lcom/android/settingslib/applications/ApplicationsState$AppEntry;->info:Landroid/content/pm/ApplicationInfo;
    if-eqz v9, :cond_nemotron_contacts_filter_next
    iget-object v10, v9, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;
    invoke-static {v10}, Lcom/android/settings/applications/manageapplications/ManageApplications$ApplicationsAdapter;->shouldHideFromInstalledApps(Ljava/lang/String;)Z
    move-result v10
    if-eqz v10, :cond_nemotron_contacts_filter_next
    invoke-virtual {p1, v6}, Ljava/util/ArrayList;->remove(I)Ljava/lang/Object;
    goto :cond_nemotron_contacts_filter_loop
    :cond_nemotron_contacts_filter_next
    add-int/lit8 v6, v6, 0x1
    goto :cond_nemotron_contacts_filter_loop
    :cond_nemotron_contacts_filter_done
'@

$helper = @'
.method public static final shouldHideFromInstalledApps(Ljava/lang/String;)Z
    .locals 1
    const-string v0, "com.google.android.contacts"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.android.lool"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "df.root"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.android.displayassistant"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.google.android.documentsui"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.android.goodlock"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "me.weishu.kernelsu"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.nemotron.core"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.sec.android.app.launcher"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.android.app.homestar"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.android.qstuner"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.systemui.navillera"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.android.settings"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.samsung.android.themedesigner"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const-string v0, "com.google.android.gms"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_nemotron_hide_app
    const/4 v0, 0x0
    return v0
    :cond_nemotron_hide_app
    const/4 v0, 0x1
    return v0
.end method

'@

$methodStart = $text.IndexOf($method, [StringComparison]::Ordinal)
$methodEnd = $text.IndexOf(".end method", $methodStart, [StringComparison]::Ordinal)
if ($methodEnd -lt 0) { throw 'Could not find the end of onRebuildComplete.' }
$contextAnchor = '    iget-object v1, p0, Lcom/android/settings/applications/manageapplications/ManageApplications$ApplicationsAdapter;->mContext:Landroidx/fragment/app/FragmentActivity;'
$contextOffset = $text.IndexOf($contextAnchor, $methodStart, $methodEnd - $methodStart, [StringComparison]::Ordinal)
if ($contextOffset -lt 0) { throw 'Could not find the app-list rebuild insertion point.' }
$text = $text.Insert($contextOffset, $filter + "`r`n")
$methodStart = $text.IndexOf($method, [StringComparison]::Ordinal)
$text = $text.Insert($methodStart, $helper)
[IO.File]::WriteAllText($adapter, $text, [Text.UTF8Encoding]::new($false))

Write-Output "Patched installed-app list: $adapter"
