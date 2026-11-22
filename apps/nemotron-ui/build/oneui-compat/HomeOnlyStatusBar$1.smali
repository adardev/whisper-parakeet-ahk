.class final Lcom/nemotron/oneui/compat/HomeOnlyStatusBar$1;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;

.method constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public run()V
    .locals 0

    invoke-static {}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->onDeferredHomeStatusBar()V
    return-void
.end method
