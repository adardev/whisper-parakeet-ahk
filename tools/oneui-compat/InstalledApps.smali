.class public final Lcom/nemotron/oneui/compat/InstalledApps;
.super Ljava/lang/Object;
.method public static load(Landroid/content/Context;)Ljava/util/List;
    .locals 8
    invoke-virtual {p0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;
    move-result-object v0
    new-instance v1, Landroid/content/Intent;
    const-string v2, "android.intent.action.MAIN"
    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
    const-string v2, "android.intent.category.LAUNCHER"
    invoke-virtual {v1, v2}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;
    const/16 v2, 0x200
    invoke-virtual {v0, v1, v2}, Landroid/content/pm/PackageManager;->queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;
    move-result-object v1
    new-instance v2, Landroid/content/pm/ResolveInfo$DisplayNameComparator;
    invoke-direct {v2, v0}, Landroid/content/pm/ResolveInfo$DisplayNameComparator;-><init>(Landroid/content/pm/PackageManager;)V
    invoke-static {v1, v2}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V
    new-instance v2, Ljava/util/ArrayList;
    invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V
    new-instance v3, Ljava/util/HashSet;
    invoke-direct {v3}, Ljava/util/HashSet;-><init>()V
    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :loop
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v4
    if-eqz v4, :done
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v4
    check-cast v4, Landroid/content/pm/ResolveInfo;
    iget-object v4, v4, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;
    iget-object v5, v4, Landroid/content/pm/ActivityInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v3, v5}, Ljava/util/HashSet;->add(Ljava/lang/Object;)Z
    move-result v6
    if-eqz v6, :loop
    iget-object v6, v4, Landroid/content/pm/ActivityInfo;->name:Ljava/lang/String;
    invoke-virtual {v0, v5}, Landroid/content/pm/PackageManager;->getLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;
    move-result-object v7
    if-eqz v7, :label
    invoke-virtual {v7}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v7
    if-eqz v7, :label
    invoke-virtual {v7}, Landroid/content/ComponentName;->getClassName()Ljava/lang/String;
    move-result-object v6
    :label
    invoke-virtual {v4, v0}, Landroid/content/pm/PackageItemInfo;->loadLabel(Landroid/content/pm/PackageManager;)Ljava/lang/CharSequence;
    move-result-object v4
    invoke-interface {v4}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;
    move-result-object v4
    new-instance v7, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;
    invoke-direct {v7, v4, v5, v6}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v2, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :loop
    :done
    return-object v2
.end method
