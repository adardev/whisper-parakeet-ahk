.class public final Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;
.super Ljava/lang/Object;

.field private static activityRef:Ljava/lang/ref/WeakReference;
.field private static showHomeStatusRunnable:Ljava/lang/Runnable;

.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar$1;
    invoke-direct {v0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar$1;-><init>()V
    sput-object v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->showHomeStatusRunnable:Ljava/lang/Runnable;
    return-void
.end method

.method private static managerFor(Lcom/sec/android/app/launcher/Launcher;)Lcom/honeyspace/sdk/HoneyScreenManager;
    .locals 4

    invoke-virtual {p0}, Lcom/sec/android/app/launcher/Launcher;->j()Lcom/honeyspace/common/di/HoneyGeneratedComponentManager;
    move-result-object v0

    invoke-virtual {p0}, Landroid/content/Context;->getDisplay()Landroid/view/Display;
    move-result-object v1
    if-eqz v1, :display_zero
    invoke-virtual {v1}, Landroid/view/Display;->getDisplayId()I
    move-result v1
    goto :component

    :display_zero
    const/4 v1, 0x0

    :component
    invoke-interface {v0, v1}, Lcom/honeyspace/common/di/HoneyGeneratedComponentManager;->generatedComponent(I)Ljava/lang/Object;
    move-result-object v0

    const-class v1, Lcom/honeyspace/common/di/HoneySpaceComponentEntryPoint;
    invoke-static {v0, v1}, Ldagger/hilt/EntryPoints;->get(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/honeyspace/common/di/HoneySpaceComponentEntryPoint;

    invoke-interface {v0}, Lcom/honeyspace/common/di/HoneySpaceComponentEntryPoint;->getHoneyScreenManager()Lcom/honeyspace/sdk/HoneyScreenManager;
    move-result-object v0
    return-object v0
.end method

.method public static attachAndApply(Lcom/sec/android/app/launcher/Launcher;)V
    .locals 2

    if-eqz p0, :done
    new-instance v0, Ljava/lang/ref/WeakReference;
    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
    sput-object v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->activityRef:Ljava/lang/ref/WeakReference;

    invoke-static {p0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->managerFor(Lcom/sec/android/app/launcher/Launcher;)Lcom/honeyspace/sdk/HoneyScreenManager;
    move-result-object v0
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->apply(Lcom/sec/android/app/launcher/Launcher;Lcom/honeyspace/sdk/HoneyScreenManager;)V

    :done
    return-void
.end method

.method public static detach(Lcom/sec/android/app/launcher/Launcher;)V
    .locals 1

    const/4 v0, 0x0
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->setImmersivePolicy(Landroid/content/Context;Z)V
    sget-object v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->activityRef:Ljava/lang/ref/WeakReference;
    if-eqz v0, :done
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    if-ne v0, p0, :done
    const/4 v0, 0x0
    sput-object v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->activityRef:Ljava/lang/ref/WeakReference;

    :done
    return-void
.end method

.method public static onStateChanged(Lcom/honeyspace/sdk/HoneyScreenManager;)V
    .locals 2

    sget-object v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->activityRef:Ljava/lang/ref/WeakReference;
    if-eqz v0, :done
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/sec/android/app/launcher/Launcher;
    if-eqz v0, :done
    invoke-static {v0, p0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->apply(Lcom/sec/android/app/launcher/Launcher;Lcom/honeyspace/sdk/HoneyScreenManager;)V

    :done
    return-void
.end method

.method private static apply(Lcom/sec/android/app/launcher/Launcher;Lcom/honeyspace/sdk/HoneyScreenManager;)V
    .locals 4

    if-eqz p0, :done
    if-eqz p1, :done

    invoke-interface {p1}, Lcom/honeyspace/sdk/HoneyScreenManager;->isNormalHomescreen()Z
    move-result v0
    if-eqz v0, :hide
    invoke-interface {p1}, Lcom/honeyspace/sdk/HoneyScreenManager;->isOnStateTransition()Z
    move-result v0
    if-nez v0, :hide
    invoke-interface {p1}, Lcom/honeyspace/sdk/HoneyScreenManager;->isOnGoingAnimationRunning()Z
    move-result v0
    if-nez v0, :hide
    invoke-static {p0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->scheduleHomeStatusBar(Landroid/content/Context;)V
    goto :done

    :hide
    const/4 v0, 0x0
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->setImmersivePolicy(Landroid/content/Context;Z)V

    :done
    return-void
.end method

.method private static scheduleHomeStatusBar(Landroid/content/Context;)V
    .locals 4

    const/4 v0, 0x0
    invoke-static {p0, v0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->setImmersivePolicy(Landroid/content/Context;Z)V
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
    move-result-object v0
    new-instance v1, Landroid/os/Handler;
    invoke-direct {v1, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
    sget-object v2, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->showHomeStatusRunnable:Ljava/lang/Runnable;
    invoke-virtual {v1, v2}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V
    # State, animation, and focus gates above and in the deferred callback
    # isolate this to stable Home. Post on the next main-loop turn so Home
    # status-bar-backed Live notifications do not animate ahead of the bar.
    const-wide/16 v3, 0x0
    invoke-virtual {v1, v2, v3, v4}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
    return-void
.end method

.method public static onDeferredHomeStatusBar()V
    .locals 3

    sget-object v0, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->activityRef:Ljava/lang/ref/WeakReference;
    if-eqz v0, :done
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/sec/android/app/launcher/Launcher;
    if-eqz v0, :done
    # Do not let an old Home timer reveal the bar after Quick Switch has
    # transferred window focus to another task.
    invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
    move-result-object v2
    if-eqz v2, :done
    invoke-virtual {v2}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v2
    invoke-virtual {v2}, Landroid/view/View;->hasWindowFocus()Z
    move-result v2
    if-eqz v2, :done
    invoke-static {v0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->managerFor(Lcom/sec/android/app/launcher/Launcher;)Lcom/honeyspace/sdk/HoneyScreenManager;
    move-result-object v1
    if-eqz v1, :done
    invoke-interface {v1}, Lcom/honeyspace/sdk/HoneyScreenManager;->isNormalHomescreen()Z
    move-result v2
    if-eqz v2, :done
    invoke-interface {v1}, Lcom/honeyspace/sdk/HoneyScreenManager;->isOnStateTransition()Z
    move-result v2
    if-nez v2, :done
    invoke-interface {v1}, Lcom/honeyspace/sdk/HoneyScreenManager;->isOnGoingAnimationRunning()Z
    move-result v1
    if-nez v1, :done
    const/4 v1, 0x1
    invoke-static {v0, v1}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->setImmersivePolicy(Landroid/content/Context;Z)V

    :done
    return-void
.end method

.method public static onExternalAppGestureStart(Landroid/content/Context;)V
    .locals 3

    # Cancel every pending Home reveal first. A delayed callback can otherwise
    # race the launcher losing focus during the lateral task switch.
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
    move-result-object v0
    new-instance v1, Landroid/os/Handler;
    invoke-direct {v1, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
    sget-object v2, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->showHomeStatusRunnable:Ljava/lang/Runnable;
    invoke-virtual {v1, v2}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V

    if-eqz p0, :done
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    const-string v1, "policy_control"
    invoke-static {v0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v2
    const-string v1, "immersive.status=apps,-com.sec.android.app.launcher"
    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :done
    const-string v1, "policy_control"
    const-string v2, "immersive.status=apps"
    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    :done
    return-void
.end method

.method private static setImmersivePolicy(Landroid/content/Context;Z)V
    .locals 4

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v1
    const-string v2, "policy_control"
    invoke-static {v1, v2}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v3
    if-eqz v3, :done

    const-string v0, "immersive.status=apps"
    invoke-virtual {v3, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v0
    if-eqz v0, :done

    const-string v0, "immersive.status=apps"
    if-eqz p1, :write
    const-string v0, "immersive.status=apps,-com.sec.android.app.launcher"

    :write
    invoke-virtual {v0, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v3
    if-nez v3, :done
    invoke-static {v1, v2, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    :done
    return-void
.end method
