.method private final setDeviceTotalRam()V
    .locals 4
    iget-object v0, p0, Lcom/honeyspace/common/performance/DevicePerfInfo;->context:Landroid/content/Context;
    const-string v1, "activity"
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/app/ActivityManager;
    new-instance v1, Landroid/app/ActivityManager$MemoryInfo;
    invoke-direct {v1}, Landroid/app/ActivityManager$MemoryInfo;-><init>()V
    invoke-virtual {v0, v1}, Landroid/app/ActivityManager;->getMemoryInfo(Landroid/app/ActivityManager$MemoryInfo;)V
    iget-wide v2, v1, Landroid/app/ActivityManager$MemoryInfo;->totalMem:J
    const/16 v0, 0xa
    shr-long/2addr v2, v0
    long-to-int v0, v2
    sput v0, Lcom/honeyspace/common/performance/DevicePerfInfo;->totalRamSize:I
    return-void
.end method
