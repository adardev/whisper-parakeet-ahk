.class public final Lcom/nemotron/oneui/compat/BackProgressAnimator;
.super Ljava/lang/Object;

# SystemUI transition integration is unavailable to this user-installed clone.
# Keep its lifecycle safe without invoking hidden framework animation APIs.
.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public reset()V
    .locals 0
    return-void
.end method

.method public removeOnBackCancelledFinishCallback()V
    .locals 0
    return-void
.end method

.method public onBackStarted(Landroid/window/BackMotionEvent;Lcom/nemotron/oneui/compat/BackProgressAnimator$ProgressCallback;)V
    .locals 0
    return-void
.end method

.method public onBackProgressed(Landroid/window/BackMotionEvent;)V
    .locals 0
    return-void
.end method

.method public onBackCancelled(Ljava/lang/Runnable;)V
    .locals 0
    invoke-interface {p1}, Ljava/lang/Runnable;->run()V
    return-void
.end method
