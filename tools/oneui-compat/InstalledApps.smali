.class public final Lcom/nemotron/oneui/compat/InstalledApps;
.super Ljava/lang/Object;

.method public static selectManually(Landroid/content/Context;Ljava/lang/String;)V
    .locals 5
    const-string v0, "nemotron_launcher"
    const/4 v1, 0x0
    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v0
    const-string v1, "manual_apps"
    invoke-static {}, Ljava/util/Collections;->emptySet()Ljava/util/Set;
    move-result-object v2
    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getStringSet(Ljava/lang/String;Ljava/util/Set;)Ljava/util/Set;
    move-result-object v2
    new-instance v3, Ljava/util/HashSet;
    invoke-direct {v3, v2}, Ljava/util/HashSet;-><init>(Ljava/util/Collection;)V
    invoke-virtual {v3, p1}, Ljava/util/HashSet;->add(Ljava/lang/Object;)Z
    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;
    move-result-object v0
    invoke-interface {v0, v1, v3}, Landroid/content/SharedPreferences$Editor;->putStringSet(Ljava/lang/String;Ljava/util/Set;)Landroid/content/SharedPreferences$Editor;
    move-result-object v0
    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V
    return-void
.end method

.method public static isManuallySelected(Landroid/content/Context;Ljava/lang/String;)Z
    .locals 3
    const-string v0, "nemotron_launcher"
    const/4 v1, 0x0
    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v0
    const-string v1, "manual_apps"
    invoke-static {}, Ljava/util/Collections;->emptySet()Ljava/util/Set;
    move-result-object v2
    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getStringSet(Ljava/lang/String;Ljava/util/Set;)Ljava/util/Set;
    move-result-object v0
    invoke-interface {v0, p1}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z
    move-result v0
    return v0
.end method

.method public static freshId(Ljava/lang/String;)Ljava/lang/String;
    .locals 3
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0, p0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    const-string p0, "_"
    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v1
    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    return-object v0
.end method

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
    const-string v6, "com.whatsapp"
    invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v6
    if-nez v6, :loop
    const-string v6, "com.sec.android.app.shealth"
    invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v6
    if-nez v6, :loop
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
    # Keep these explicitly selectable even when Samsung marks Health disabled
    # or its launcher component is omitted from the normal package query.
    const-string v5, "com.whatsapp"
    invoke-virtual {v3, v5}, Ljava/util/HashSet;->add(Ljava/lang/Object;)Z
    move-result v6
    if-eqz v6, :manual_health
    const-string v6, "WhatsApp"
    const-string v7, "com.whatsapp.Main"
    new-instance v4, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;
    invoke-direct {v4, v6, v5, v7}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    const/4 v6, 0x0
    invoke-virtual {v2, v6, v4}, Ljava/util/ArrayList;->add(ILjava/lang/Object;)V
    :manual_health
    const-string v5, "com.sec.android.app.shealth"
    invoke-virtual {v3, v5}, Ljava/util/HashSet;->add(Ljava/lang/Object;)Z
    move-result v6
    if-eqz v6, :manual_apps_done
    const-string v6, "Samsung Health"
    const-string v7, "com.samsung.android.app.shealth.home.HomeMainActivity"
    new-instance v4, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;
    invoke-direct {v4, v6, v5, v7}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    const/4 v6, 0x0
    invoke-virtual {v2, v6, v4}, Ljava/util/ArrayList;->add(ILjava/lang/Object;)V
    :manual_apps_done
    return-object v2
.end method
