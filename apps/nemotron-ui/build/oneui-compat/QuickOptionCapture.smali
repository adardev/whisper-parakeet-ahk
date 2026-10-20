.class public final Lcom/nemotron/oneui/compat/QuickOptionCapture;
.super Ljava/lang/Object;

# Capture only this Activity's view hierarchy, never the privileged framebuffer.
.method public static capture(Landroid/content/Context;II)Landroid/graphics/Bitmap;
    .locals 5
    :try_start
    if-lez p1, :empty
    if-lez p2, :empty
    const/16 v0, 0x10
    :unwrap
    instance-of v1, p0, Landroid/app/Activity;
    if-nez v1, :activity
    instance-of v1, p0, Landroid/content/ContextWrapper;
    if-eqz v1, :empty
    add-int/lit8 v0, v0, -0x1
    if-lez v0, :empty
    check-cast p0, Landroid/content/ContextWrapper;
    invoke-virtual {p0}, Landroid/content/ContextWrapper;->getBaseContext()Landroid/content/Context;
    move-result-object p0
    goto :unwrap
    :activity
    check-cast p0, Landroid/app/Activity;
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
    move-result-object v0
    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v0
    invoke-virtual {v0}, Landroid/view/View;->getRootView()Landroid/view/View;
    move-result-object v0
    sget-object v1, Landroid/graphics/Bitmap$Config;->ARGB_8888:Landroid/graphics/Bitmap$Config;
    invoke-static {p1, p2, v1}, Landroid/graphics/Bitmap;->createBitmap(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;
    move-result-object v1
    new-instance v2, Landroid/graphics/Canvas;
    invoke-direct {v2, v1}, Landroid/graphics/Canvas;-><init>(Landroid/graphics/Bitmap;)V
    const/4 v3, 0x2
    new-array v3, v3, [I
    invoke-virtual {v0, v3}, Landroid/view/View;->getLocationOnScreen([I)V
    const/4 v4, 0x0
    aget v4, v3, v4
    int-to-float v4, v4
    const/4 p1, 0x1
    aget p1, v3, p1
    int-to-float p1, p1
    invoke-virtual {v2, v4, p1}, Landroid/graphics/Canvas;->translate(FF)V
    invoke-virtual {v0, v2}, Landroid/view/View;->draw(Landroid/graphics/Canvas;)V
    return-object v1
    :try_end
    .catch Ljava/lang/RuntimeException; {:try_start .. :try_end} :failed
    .catch Ljava/lang/OutOfMemoryError; {:try_start .. :try_end} :failed
    :failed
    move-exception v0
    :empty
    const/4 v0, 0x0
    return-object v0
.end method
