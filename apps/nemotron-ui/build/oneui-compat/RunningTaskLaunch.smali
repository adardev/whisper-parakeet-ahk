.class public final Lcom/nemotron/oneui/compat/RunningTaskLaunch;
.super Ljava/lang/Object;

.method public static findTaskId(Landroid/content/Context;Landroid/content/Intent;)I
    .locals 6

    invoke-virtual {p1}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v0
    if-eqz v0, :task_intent_package
    invoke-virtual {v0}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v0
    goto :task_package_ready

    :task_intent_package
    invoke-virtual {p1}, Landroid/content/Intent;->getPackage()Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :task_resolve_intent
    goto :task_package_ready

    :task_resolve_intent
    invoke-virtual {p0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;
    move-result-object v1
    invoke-virtual {p1, v1}, Landroid/content/Intent;->resolveActivity(Landroid/content/pm/PackageManager;)Landroid/content/ComponentName;
    move-result-object v1
    if-eqz v1, :task_not_found
    invoke-virtual {v1}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v0

    :task_package_ready
    if-eqz v0, :task_not_found
    const-string v1, "activity"
    invoke-virtual {p0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Landroid/app/ActivityManager;
    const/16 v2, 0x32
    invoke-virtual {v1, v2}, Landroid/app/ActivityManager;->getRunningTasks(I)Ljava/util/List;
    move-result-object v1
    if-eqz v1, :task_not_found
    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1

    :task_loop
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :task_not_found
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Landroid/app/ActivityManager$RunningTaskInfo;
    iget-object v3, v2, Landroid/app/TaskInfo;->baseActivity:Landroid/content/ComponentName;
    if-eqz v3, :task_check_top
    invoke-virtual {v3}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {v0, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-eqz v4, :task_check_top
    iget v0, v2, Landroid/app/ActivityManager$RunningTaskInfo;->id:I
    return v0

    :task_check_top
    iget-object v3, v2, Landroid/app/TaskInfo;->topActivity:Landroid/content/ComponentName;
    if-eqz v3, :task_loop
    invoke-virtual {v3}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {v0, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-eqz v4, :task_loop
    iget v0, v2, Landroid/app/ActivityManager$RunningTaskInfo;->id:I
    return v0

    :task_not_found
    const/4 v0, -0x1
    return v0
.end method

.method public static launchRunningTask(Landroid/app/Activity;Landroid/content/Intent;)Z
    .locals 2
    invoke-static {p0, p1}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->findTaskId(Landroid/content/Context;Landroid/content/Intent;)I
    move-result v0
    if-ltz v0, :no_existing_task
    const v1, 0x10000
    invoke-virtual {p1, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;
    invoke-virtual {p0, p1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    const/4 v0, 0x1
    return v0
    :no_existing_task
    const/4 v0, 0x0
    return v0
.end method
.method public static findTopActivity(Landroid/content/Context;Ljava/lang/String;)Landroid/content/ComponentName;
    .locals 6
    if-eqz p1, :top_not_found
    const-string v0, "activity"
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/app/ActivityManager;
    const/16 v5, 0x32
    invoke-virtual {v0, v5}, Landroid/app/ActivityManager;->getRunningTasks(I)Ljava/util/List;
    move-result-object v1
    if-eqz v1, :top_not_found
    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :top_task_loop
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v5
    if-eqz v5, :top_not_found
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Landroid/app/ActivityManager$RunningTaskInfo;
    iget-object v3, v2, Landroid/app/TaskInfo;->topActivity:Landroid/content/ComponentName;
    if-eqz v3, :top_check_base
    invoke-virtual {v3}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {p1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v5
    if-eqz v5, :top_check_base
    return-object v3
    :top_check_base
    iget-object v3, v2, Landroid/app/TaskInfo;->baseActivity:Landroid/content/ComponentName;
    if-eqz v3, :top_task_loop
    invoke-virtual {v3}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {p1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v5
    if-eqz v5, :top_task_loop
    return-object v3
    :top_not_found
    const/4 v0, 0x0
    return-object v0
.end method

# Warm app-icon launches share this path through LauncherApps.startMainActivity.
# Reopen the exact live activity so Android delivers a new intent instead of
# displaying the stale task snapshot. Return false for cold apps so Samsung's
# normal launcher launch path remains unchanged.
.method public static launchWarmComponent(Landroid/content/Context;Landroid/content/ComponentName;Landroid/os/Bundle;)Z
    .locals 6
    if-eqz p1, :component_fallback
    invoke-virtual {p1}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v0
    const-string v1, "com.sec.android.app.launcher"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :component_not_home
    goto :component_fallback
    :component_not_home
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->isProcessRunning(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v1
    if-eqz v1, :component_fallback
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->findTopActivity(Landroid/content/Context;Ljava/lang/String;)Landroid/content/ComponentName;
    move-result-object v2
    if-eqz v2, :component_fallback
    new-instance v3, Landroid/content/Intent;
    invoke-direct {v3}, Landroid/content/Intent;-><init>()V
    invoke-virtual {v3, v2}, Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;
    const-string v4, "com.nemotron.oneui.extra.SKIP_TASK_SNAPSHOT"
    const/4 v5, 0x1
    invoke-virtual {v3, v4, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;
    const v4, 0x30000000
    invoke-virtual {v3, v4}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;
    invoke-virtual {p0, v3, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V
    const/4 v0, 0x1
    return v0
    :component_fallback
    const/4 v0, 0x0
    return v0
.end method

.method public static isProcessRunning(Landroid/content/Context;Ljava/lang/String;)Z
    .locals 4

    if-eqz p1, :not_running
    const-string v0, "activity"
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/app/ActivityManager;
    invoke-virtual {v0}, Landroid/app/ActivityManager;->getRunningAppProcesses()Ljava/util/List;
    move-result-object v0
    if-eqz v0, :not_running
    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v0

    :process_loop
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
    move-result v1
    if-eqz v1, :not_running
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Landroid/app/ActivityManager$RunningAppProcessInfo;
    iget-object v2, v1, Landroid/app/ActivityManager$RunningAppProcessInfo;->processName:Ljava/lang/String;
    if-eqz v2, :process_loop
    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v3
    if-eqz v3, :process_loop
    const/4 v0, 0x1
    return v0

    :not_running
    const/4 v0, 0x0
    return v0
.end method

.method public static intentProcessRunning(Landroid/content/Context;Landroid/content/Intent;)Z
    .locals 2

    invoke-virtual {p1}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v0
    if-eqz v0, :intent_package
    invoke-virtual {v0}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v0
    goto :package_ready

    :intent_package
    invoke-virtual {p1}, Landroid/content/Intent;->getPackage()Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :resolve_intent
    goto :package_ready

    :resolve_intent
    invoke-virtual {p0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;
    move-result-object v1
    invoke-virtual {p1, v1}, Landroid/content/Intent;->resolveActivity(Landroid/content/pm/PackageManager;)Landroid/content/ComponentName;
    move-result-object v1
    if-eqz v1, :not_running
    invoke-virtual {v1}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v0

    :package_ready
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->isProcessRunning(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v0
    return v0

    :not_running
    const/4 v0, 0x0
    return v0
.end method
