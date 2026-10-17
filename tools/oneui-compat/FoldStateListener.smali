.class public Lcom/nemotron/oneui/compat/FoldStateListener;
.super Ljava/lang/Object;
.implements Landroid/hardware/devicestate/DeviceStateManager$DeviceStateCallback;

.field private final consumer:Ljava/util/function/Consumer;

.method public constructor <init>(Landroid/content/Context;Ljava/util/function/Consumer;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p2, p0, Lcom/nemotron/oneui/compat/FoldStateListener;->consumer:Ljava/util/function/Consumer;
    return-void
.end method

.method public onStateChanged(I)V
    .locals 2
    iget-object v0, p0, Lcom/nemotron/oneui/compat/FoldStateListener;->consumer:Ljava/util/function/Consumer;
    sget-object v1, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
    invoke-interface {v0, v1}, Ljava/util/function/Consumer;->accept(Ljava/lang/Object;)V
    return-void
.end method

.method public onDeviceStateChanged(Landroid/hardware/devicestate/DeviceState;)V
    .locals 1
    const/4 v0, 0x0
    invoke-virtual {p0, v0}, Lcom/nemotron/oneui/compat/FoldStateListener;->onStateChanged(I)V
    return-void
.end method
