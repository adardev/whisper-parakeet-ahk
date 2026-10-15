.class public final Lcom/nemotron/voiceime/record/HoneyBoardDialogs;
.super Ljava/lang/Object;

.field private static service:Ljava/lang/ref/WeakReference;

.method public static bind(Landroid/inputmethodservice/InputMethodService;)V
    .locals 1
    new-instance v0, Ljava/lang/ref/WeakReference;
    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
    sput-object v0, Lcom/nemotron/voiceime/record/HoneyBoardDialogs;->service:Ljava/lang/ref/WeakReference;
    return-void
.end method

.method public static attach(Landroid/view/Window;)V
    .locals 2
    sget-object v0, Lcom/nemotron/voiceime/record/HoneyBoardDialogs;->service:Ljava/lang/ref/WeakReference;
    if-eqz v0, :done
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/inputmethodservice/InputMethodService;
    if-eqz v0, :done
    invoke-virtual {v0}, Landroid/inputmethodservice/InputMethodService;->getWindow()Landroid/app/Dialog;
    move-result-object v0
    if-eqz v0, :done
    invoke-virtual {v0}, Landroid/app/Dialog;->getWindow()Landroid/view/Window;
    move-result-object v0
    if-eqz v0, :done
    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v0
    invoke-virtual {v0}, Landroid/view/View;->getWindowToken()Landroid/os/IBinder;
    move-result-object v0
    if-eqz v0, :done
    invoke-virtual {p0}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;
    move-result-object v1
    iput-object v0, v1, Landroid/view/WindowManager$LayoutParams;->token:Landroid/os/IBinder;
    const/16 v0, 0x3eb
    iput v0, v1, Landroid/view/WindowManager$LayoutParams;->type:I
    invoke-virtual {p0, v1}, Landroid/view/Window;->setAttributes(Landroid/view/WindowManager$LayoutParams;)V
    :done
    return-void
.end method
