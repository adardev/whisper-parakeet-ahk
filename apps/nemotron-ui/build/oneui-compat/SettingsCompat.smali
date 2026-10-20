.class public final Lcom/nemotron/oneui/compat/SettingsCompat;
.super Ljava/lang/Object;

.method public static getSecureString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    .locals 1
    :try_start
    invoke-static {p0, p1}, Landroid/provider/Settings$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    return-object v0
    :try_end
    .catch Ljava/lang/SecurityException; {:try_start .. :try_end} :denied
    :denied
    move-exception v0
    const/4 v0, 0x0
    return-object v0
.end method

.method public static getSystemString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    .locals 1
    :try_start
    invoke-static {p0, p1}, Landroid/provider/Settings$System;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    return-object v0
    :try_end
    .catch Ljava/lang/SecurityException; {:try_start .. :try_end} :denied
    :denied
    move-exception v0
    const/4 v0, 0x0
    return-object v0
.end method

.method public static getGlobalString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    .locals 1
    :try_start
    invoke-static {p0, p1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    return-object v0
    :try_end
    .catch Ljava/lang/SecurityException; {:try_start .. :try_end} :denied
    :denied
    move-exception v0
    const/4 v0, 0x0
    return-object v0
.end method
