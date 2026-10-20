.method public getDisplay()Landroid/view/Display;
    .locals 2
    const-string v0, "display"
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/hardware/display/DisplayManager;
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroid/hardware/display/DisplayManager;->getDisplay(I)Landroid/view/Display;
    move-result-object v0
    return-object v0
.end method

.method public createWindowContext(ILandroid/os/Bundle;)Landroid/content/Context;
    .locals 1
    invoke-static {p0, p1, p2}, Lcom/nemotron/oneui/compat/LauncherCompat;->createWindowContext(Landroid/content/Context;ILandroid/os/Bundle;)Landroid/content/Context;
    move-result-object v0
    return-object v0
.end method
