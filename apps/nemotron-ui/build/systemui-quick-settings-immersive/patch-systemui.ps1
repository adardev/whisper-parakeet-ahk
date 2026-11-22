param([Parameter(Mandatory=$true)][string]$DecodedRoot)
$ErrorActionPreference = 'Stop'
$file = Join-Path $DecodedRoot 'smali_classes3/com/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl$commandQueueCallback$1.smali'
$text = [IO.File]::ReadAllText($file)
$start = $text.IndexOf('.method public final showTransient(IIZ)V')
$end = $text.IndexOf(".end method", $start)
if ($start -lt 0 -or $end -lt 0) { throw 'Expected showTransient callback method was not found.' }
$end += '.end method'.Length
$old = $text.Substring($start, $end - $start)
if ($old.Contains('animateExpandSettingsPanel(Ljava/lang/String;)V')) { throw 'Quick Settings hook is already present; refusing to patch twice.' }
$new = @'
.method public final showTransient(IIZ)V
    .locals 3

    iget v0, p0, Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl$commandQueueCallback$1;->$thisDisplayId:I
    if-ne p1, v0, :cond_1

    invoke-static {}, Landroid/view/WindowInsets$Type;->statusBars()I
    move-result v0
    and-int/2addr v0, p2
    if-eqz v0, :cond_1

    # A status-bar transient is requested when the user swipes from the top
    # edge while immersive mode has hidden the bar. Route that same gesture
    # through SystemUI's normal direct Quick Settings expansion command.
    iget-object v0, p0, Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl$commandQueueCallback$1;->this$0:Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl;
    iget-object v1, v0, Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl;->commandQueue:Lcom/android/systemui/statusbar/CommandQueue;
    const/4 v2, 0x0
    invoke-virtual {v1, v2}, Lcom/android/systemui/statusbar/CommandQueue;->animateExpandSettingsPanel(Ljava/lang/String;)V

    const-class v0, Lcom/android/systemui/statusbar/phone/CentralSurfaces;
    sget-object v1, Lcom/android/systemui/Dependency;->sDependency:Lcom/android/systemui/Dependency;
    invoke-virtual {v1, v0}, Lcom/android/systemui/Dependency;->getDependencyInner(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/android/systemui/statusbar/phone/CentralSurfaces;
    if-eqz v0, :cond_0

    check-cast v0, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    const/4 v1, 0x1
    iput-boolean v1, v0, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mNoAnimationOnNextBarModeChange:Z

    :cond_0
    iget-object v0, p0, Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl$commandQueueCallback$1;->this$0:Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl;
    iget-object v0, v0, Lcom/android/systemui/statusbar/data/repository/StatusBarModePerDisplayRepositoryImpl;->_isTransientShown:Lkotlinx/coroutines/flow/StateFlowImpl;
    sget-object v1, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    const/4 v2, 0x0
    invoke-virtual {v0, v2, v1}, Lkotlinx/coroutines/flow/StateFlowImpl;->updateState(Ljava/lang/Object;Ljava/lang/Object;)Z

    :cond_1
    return-void
.end method
'@
$text = $text.Remove($start, $end - $start).Insert($start, $new.TrimEnd())
[IO.File]::WriteAllText($file, $text, [Text.UTF8Encoding]::new($false))
