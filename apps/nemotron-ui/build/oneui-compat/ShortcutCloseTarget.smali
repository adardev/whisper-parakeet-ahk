.class public final Lcom/nemotron/oneui/compat/ShortcutCloseTarget;
.super Ljava/lang/Object;

# Resolve Nemotron-pinned app shortcuts to the native app component so One UI's
# existing close-target animation can find the real icon. No animation is drawn.
.method public static attachCookie(Lcom/honeyspace/sdk/transition/ShellTransition$Info;Landroid/app/ActivityOptions;)V
    .locals 2
    :cookie_start
    invoke-virtual {p0}, Lcom/honeyspace/sdk/transition/ShellTransition$Info;->getId()I
    move-result v0
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v0
    sget-object v1, Lcom/honeyspace/transition/utils/ObjectWrapper;->Companion:Lcom/honeyspace/transition/utils/ObjectWrapper$Companion;
    invoke-virtual {v1, v0}, Lcom/honeyspace/transition/utils/ObjectWrapper$Companion;->wrap(Ljava/lang/Object;)Landroid/os/IBinder;
    move-result-object v0
    invoke-static {p1, v0}, Lcom/android/systemui/shared/launcher/ActivityOptionsCompat;->setLaunchCookie(Landroid/app/ActivityOptions;Landroid/os/IBinder;)V
    :cookie_end
    .catch Ljava/lang/RuntimeException; {:cookie_start .. :cookie_end} :cookie_failed
    .catch Ljava/lang/LinkageError; {:cookie_start .. :cookie_end} :cookie_failed
    return-void
    :cookie_failed
    move-exception v0
    return-void
.end method

# Only Nemotron's own app-launch shortcuts qualify. Ordinary deep links keep
# Samsung's original behavior. No package installation grants placement.
.method public static targetPackage(Lcom/honeyspace/sdk/source/entity/BaseItem;)Ljava/lang/String;
    .locals 4
    instance-of v0, p0, Lcom/honeyspace/sdk/source/entity/ShortcutItem;
    if-eqz v0, :none
    check-cast p0, Lcom/honeyspace/sdk/source/entity/ShortcutItem;
    invoke-virtual {p0}, Lcom/honeyspace/sdk/source/entity/ShortcutItem;->getIntent()Landroid/content/Intent;
    move-result-object v0
    const-string v1, "com.nemotron.voiceime2"
    invoke-virtual {v0}, Landroid/content/Intent;->getPackage()Ljava/lang/String;
    move-result-object v2
    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-nez v2, :owned
    invoke-virtual {v0}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v2
    if-eqz v2, :none
    invoke-virtual {v2}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v2
    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-nez v2, :owned

    # New picker entries point directly at the app activity. Recognize only
    # stable Nemotron IDs whose embedded package matches the component.
    invoke-virtual {p0}, Lcom/honeyspace/sdk/source/entity/ShortcutItem;->getShortcutId()Ljava/lang/String;
    move-result-object v2
    if-eqz v2, :none
    const-string v3, "nemotron_app_"
    invoke-virtual {v2, v3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
    move-result v3
    if-eqz v3, :none
    # Shortcut ID format is nemotron_app_<package>_<timestamp>. Strip the
    # trailing timestamp before matching the activity's real package name.
    const/16 v1, 0x5f
    invoke-virtual {v2, v1}, Ljava/lang/String;->lastIndexOf(I)I
    move-result v1
    if-ltz v1, :none
    const/16 v3, 0xd
    invoke-virtual {v2, v3, v1}, Ljava/lang/String;->substring(II)Ljava/lang/String;
    move-result-object v2
    invoke-virtual {v0}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v3
    if-eqz v3, :none
    invoke-virtual {v3}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v3
    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-eqz v2, :none
    return-object v3

    :owned
    const-string v1, "pkg"
    invoke-virtual {v0, v1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    if-eqz v1, :from_id
    return-object v1
    :from_id
    invoke-virtual {p0}, Lcom/honeyspace/sdk/source/entity/ShortcutItem;->getShortcutId()Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :none
    const-string v1, "shortcut_"
    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
    move-result v1
    if-eqz v1, :none
    const/16 v1, 0x9
    invoke-virtual {v0, v1}, Ljava/lang/String;->substring(I)Ljava/lang/String;
    move-result-object v0
    const-string v1, "_[0-9]+$"
    const-string v2, ""
    invoke-virtual {v0, v1, v2}, Ljava/lang/String;->replaceFirst(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    return-object v0
    :none
    const/4 v0, 0x0
    return-object v0
.end method

.method public static matches(Lcom/honeyspace/sdk/source/entity/BaseItem;Lcom/honeyspace/sdk/transition/CloseTarget$Key;)Z
    .locals 3
    invoke-static {p0}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->targetPackage(Lcom/honeyspace/sdk/source/entity/BaseItem;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :no
    invoke-virtual {p1}, Lcom/honeyspace/sdk/transition/CloseTarget$Key;->getPackageName()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :no
    check-cast p0, Lcom/honeyspace/sdk/source/entity/ShortcutItem;
    invoke-virtual {p0}, Lcom/honeyspace/sdk/source/entity/ShortcutItem;->getUser()Landroid/os/UserHandle;
    move-result-object v0
    invoke-virtual {p1}, Lcom/honeyspace/sdk/transition/CloseTarget$Key;->getUser()Landroid/os/UserHandle;
    move-result-object v1
    invoke-virtual {v0, v1}, Landroid/os/UserHandle;->equals(Ljava/lang/Object;)Z
    move-result v0
    return v0
    :no
    const/4 v0, 0x0
    return v0
.end method

.method public static value(Landroid/view/View;Landroid/content/ComponentName;Lcom/honeyspace/sdk/source/entity/ModelItemSupplier;)Lcom/honeyspace/sdk/transition/CloseTarget$Value;
    .locals 4
    invoke-interface {p2}, Lcom/honeyspace/sdk/source/entity/ModelItemSupplier;->getItem()Lcom/honeyspace/sdk/source/entity/BaseItem;
    move-result-object v0
    instance-of v1, v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    if-eqz v1, :shortcut_item
    check-cast v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/AppItem;->getComponent()Lcom/honeyspace/sdk/source/entity/ComponentKey;
    move-result-object v2
    invoke-virtual {v2}, Lcom/honeyspace/sdk/source/entity/ComponentKey;->getComponentName()Landroid/content/ComponentName;
    move-result-object p1
    const/4 v1, 0x0
    goto :result
    :shortcut_item
    instance-of v1, v0, Lcom/honeyspace/sdk/source/entity/ShortcutItem;
    if-eqz v1, :result
    invoke-static {v0}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->targetPackage(Lcom/honeyspace/sdk/source/entity/BaseItem;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :result
    :try_start
    invoke-virtual {p0}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v2
    invoke-virtual {v2}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;
    move-result-object v2
    invoke-virtual {v2, v0}, Landroid/content/pm/PackageManager;->getLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;
    move-result-object v2
    if-eqz v2, :result
    invoke-virtual {v2}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object p1
    const-string v2, "NemotronCloseTarget"
    invoke-static {v2, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end
    .catch Ljava/lang/RuntimeException; {:try_start .. :try_end} :failed
    goto :result
    :failed
    move-exception v2
    :result
    new-instance v0, Lcom/honeyspace/sdk/transition/CloseTarget$Value;
    invoke-direct {v0, p0, p1, v1}, Lcom/honeyspace/sdk/transition/CloseTarget$Value;-><init>(Landroid/view/View;Landroid/content/ComponentName;Z)V
    return-object v0
.end method
