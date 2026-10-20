.method public static getWindowCornerRadius(Landroid/content/Context;)F
    .locals 1
    invoke-static {p0}, Lcom/nemotron/oneui/compat/LauncherCompat;->getDisplay(Landroid/content/Context;)Landroid/view/Display;
    move-result-object v0
    invoke-virtual {p0, v0}, Landroid/content/Context;->createDisplayContext(Landroid/view/Display;)Landroid/content/Context;
    move-result-object p0
    invoke-static {p0}, Lcom/android/internal/policy/ScreenDecorationsUtils;->getWindowCornerRadius(Landroid/content/Context;)F
    move-result v0
    return v0
.end method
