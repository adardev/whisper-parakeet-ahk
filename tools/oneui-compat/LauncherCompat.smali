.class public final Lcom/nemotron/oneui/compat/LauncherCompat;
.super Ljava/lang/Object;

.field private static context:Landroid/content/Context;

.method public static bindServiceAsUser(Landroid/content/Context;Landroid/content/Intent;Landroid/content/ServiceConnection;ILandroid/os/UserHandle;)Z
    .locals 1
    :try_start
    invoke-virtual {p0, p1, p2, p3, p4}, Landroid/content/Context;->bindServiceAsUser(Landroid/content/Intent;Landroid/content/ServiceConnection;ILandroid/os/UserHandle;)Z
    move-result v0
    return v0
    :try_end
    .catch Ljava/lang/SecurityException; {:try_start .. :try_end} :denied
    :denied
    move-exception v0
    const/4 v0, 0x0
    return v0
.end method

.method public static bindService(Landroid/content/Context;Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z
    .locals 1
    :try_start
    invoke-virtual {p0, p1, p2, p3}, Landroid/content/Context;->bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z
    move-result v0
    return v0
    :try_end
    .catch Ljava/lang/SecurityException; {:try_start .. :try_end} :denied
    :denied
    move-exception v0
    const/4 v0, 0x0
    return v0
.end method

.method public static unbindService(Landroid/content/Context;Landroid/content/ServiceConnection;)V
    .locals 1
    :try_start
    invoke-virtual {p0, p1}, Landroid/content/Context;->unbindService(Landroid/content/ServiceConnection;)V
    :try_end
    .catch Ljava/lang/IllegalArgumentException; {:try_start .. :try_end} :missing
    return-void
    :missing
    move-exception v0
    return-void
.end method

.method public static init(Landroid/content/Context;)V
    .locals 0
    sput-object p0, Lcom/nemotron/oneui/compat/LauncherCompat;->context:Landroid/content/Context;
    return-void
.end method

.method public static getDeviceStateManager()Landroid/hardware/devicestate/DeviceStateManager;
    .locals 2
    sget-object v0, Lcom/nemotron/oneui/compat/LauncherCompat;->context:Landroid/content/Context;
    const-class v1, Landroid/hardware/devicestate/DeviceStateManager;
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/hardware/devicestate/DeviceStateManager;
    return-object v0
.end method

.method public static getWindowBounds(Landroid/content/res/Configuration;)Landroid/graphics/Rect;
    .locals 2
    sget-object v0, Lcom/nemotron/oneui/compat/LauncherCompat;->context:Landroid/content/Context;
    const-class v1, Landroid/view/WindowManager;
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/view/WindowManager;
    invoke-interface {v0}, Landroid/view/WindowManager;->getMaximumWindowMetrics()Landroid/view/WindowMetrics;
    move-result-object v0
    invoke-virtual {v0}, Landroid/view/WindowMetrics;->getBounds()Landroid/graphics/Rect;
    move-result-object v0
    new-instance v1, Landroid/graphics/Rect;
    invoke-direct {v1, v0}, Landroid/graphics/Rect;-><init>(Landroid/graphics/Rect;)V
    return-object v1
.end method

.method public static getRoleHolders(Lcom/samsung/android/app/SemRoleManager;Ljava/lang/String;)Ljava/util/List;
    .locals 1
    :try_start
    invoke-virtual {p0, p1}, Lcom/samsung/android/app/SemRoleManager;->getRoleHolders(Ljava/lang/String;)Ljava/util/List;
    move-result-object v0
    return-object v0
    :try_end
    .catch Ljava/lang/SecurityException; {:try_start .. :try_end} :denied
    :denied
    move-exception v0
    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
    move-result-object v0
    return-object v0
.end method

.method public static getDisplay(Landroid/content/Context;)Landroid/view/Display;
    .locals 2
    :try_start
    invoke-virtual {p0}, Landroid/content/Context;->getDisplay()Landroid/view/Display;
    move-result-object v0
    return-object v0
    :try_end
    .catch Ljava/lang/UnsupportedOperationException; {:try_start .. :try_end} :fallback
    :fallback
    move-exception v0
    const-string v0, "display"
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/hardware/display/DisplayManager;
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroid/hardware/display/DisplayManager;->getDisplay(I)Landroid/view/Display;
    move-result-object v0
    return-object v0
.end method

.method public static createWindowContext(Landroid/content/Context;ILandroid/os/Bundle;)Landroid/content/Context;
    .locals 1
    invoke-static {p0}, Lcom/nemotron/oneui/compat/LauncherCompat;->getDisplay(Landroid/content/Context;)Landroid/view/Display;
    move-result-object v0
    invoke-virtual {p0, v0, p1, p2}, Landroid/content/Context;->createWindowContext(Landroid/view/Display;ILandroid/os/Bundle;)Landroid/content/Context;
    move-result-object v0
    return-object v0
.end method
