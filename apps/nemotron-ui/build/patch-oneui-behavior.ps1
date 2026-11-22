param(
    [Parameter(Mandatory=$true)][string]$DecodedRoot,
    [switch]$StockHost
)
$ErrorActionPreference = 'Stop'
function Edit-Class([string]$Name, [scriptblock]$Edit) {
    $file = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter $Name | Select-Object -First 1
    if (-not $file) { throw "Missing One UI class $Name" }
    $text = [IO.File]::ReadAllText($file.FullName)
    $result = & $Edit $text
    [IO.File]::WriteAllText($file.FullName, $result, [Text.UTF8Encoding]::new($false))
}
function Edit-RelativePath([string]$RelativePath, [scriptblock]$Edit) {
    $file = Join-Path $DecodedRoot $RelativePath
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing One UI class path $RelativePath" }
    $text = [IO.File]::ReadAllText($file)
    $result = & $Edit $text
    [IO.File]::WriteAllText($file, $result, [Text.UTF8Encoding]::new($false))
}
if ($StockHost) {
    # Remove only the runtime status-bar height from scene insets when apps use
    # Nemotron immersive mode; preserve each app's additional safe-area inset.
    $compatPath = Join-Path $DecodedRoot 'smali_classes6/com/nemotron/oneui/compat'
    New-Item -ItemType Directory -Path $compatPath -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/ImmersiveRecentsInsets.smali') -Destination (Join-Path $compatPath 'ImmersiveRecentsInsets.smali') -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/RunningTaskLaunch.smali') -Destination (Join-Path $compatPath 'RunningTaskLaunch.smali') -Force
    Edit-Class 'ThumbnailData$Companion.smali' {
        param($text)
        $oldHook = '(?m)^[ \t]*invoke-static \{p0\}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forSnapshotContentInsets\(Landroid/graphics/Rect;\)Landroid/graphics/Rect;\r?\n[ \t]*move-result-object p0\r?\n?'
        if ([regex]::Matches($text, $oldHook).Count -gt 1) { throw 'Duplicate snapshot-construction inset hooks found.' }
        [regex]::Replace($text, $oldHook, '')
    }
    # Normalize task insets at read time, not only when a TaskSnapshot creates
    # ThumbnailData. Samsung may refresh a snapshot with a stale status-bar
    # inset after returning to Recents. Use the same zero top crop as the
    # immersive scene/transition; preserve other edges and never mutate the
    # cached Rect.
    Edit-Class 'TaskSceneData.smali' {
        param($text)
        $pattern = '(?s)\.method public final getInsets\(\)Landroid/graphics/Rect;\s+\.locals 0\s+iget-object p0, p0, Lcom/honeyspace/ui/common/taskScene/TaskSceneData;->insets:Landroid/graphics/Rect;\s+return-object p0\s+\.end method'
        if ([regex]::Matches($text, $pattern).Count -ne 1) { throw 'TaskSceneData inset getter was not unique.' }
        $replacement = @'
.method public final getInsets()Landroid/graphics/Rect;
    .locals 1

    iget-object v0, p0, Lcom/honeyspace/ui/common/taskScene/TaskSceneData;->insets:Landroid/graphics/Rect;
    invoke-static {v0}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forSnapshotContentInsets(Landroid/graphics/Rect;)Landroid/graphics/Rect;
    move-result-object v0
    return-object v0
.end method
'@
        [regex]::Replace($text, $pattern, $replacement.Trim(), 1)
    }
    Edit-Class 'TaskSceneViewKt.smali' {
        param($text)
        $site = '(?m)^    iget-object v1, v1, Lcom/android/systemui/shared/recents/model/ThumbnailData;->insets:Landroid/graphics/Rect;\r?\n\s*\r?\n    new-instance v2, Landroid/graphics/RectF;'
        if ([regex]::Matches($text, $site).Count -ne 1) { throw 'TaskSceneView inset read site was not unique.' }
        $hook = "    iget-object v1, v1, Lcom/android/systemui/shared/recents/model/ThumbnailData;->insets:Landroid/graphics/Rect;`n    invoke-static {v1}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forSnapshotContentInsets(Landroid/graphics/Rect;)Landroid/graphics/Rect;`n    move-result-object v1`n`n    new-instance v2, Landroid/graphics/RectF;"
        [regex]::Replace($text, $site, $hook, 1)
    }
    # Keep Samsung's native scene scale values so Recents snapshots retain the
    # original app-window aspect ratio and size.
    Edit-Class 'RecentStyler.smali' {
        param($text)
        $sites = @(
            @{ Method='getSceneInsets'; Reg='v14' },
            @{ Method='getStandardInsets'; Reg='v17' },
            @{ Method='getLayoutInets'; Reg='v15' }
        )
        foreach ($site in $sites) {
            $pattern = 'invoke-virtual \{v2\}, Lcom/honeyspace/common/recents/insetsmanager/InsetsData;->' + $site.Method + '\(\)Landroid/graphics/Insets;\s+move-result-object ' + $site.Reg
            if ([regex]::Matches($text, $pattern).Count -ne 1) { throw "Recents style inset site $($site.Method) not uniquely found." }
            $register = $site.Reg
            if ($register -eq 'v17') {
                $insertion = '$0' + "`n    move-object/from16 v44, p1`n    move-object/from16 v45, v17`n    invoke-static/range {v44 .. v45}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forScene(Landroid/content/Context;Landroid/graphics/Insets;)Landroid/graphics/Insets;`n    move-result-object v17"
            } else {
                $insertion = '$0' + "`n    move-object/from16 v44, p1`n    move-object/from16 v45, $register`n    invoke-static/range {v44 .. v45}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forScene(Landroid/content/Context;Landroid/graphics/Insets;)Landroid/graphics/Insets;`n    move-result-object $register"
            }
            $text = [regex]::Replace($text, $pattern, $insertion, 1)
        }
        $topPattern = 'invoke-interface \{v2, v6, v8, v5, v11\}, Lcom/honeyspace/ui/common/taskChangerLayout/LayoutStyle;->getSceneTopMargin\(Landroid/content/res/Resources;IZLandroid/graphics/RectF;\)I\s+move-result v25'
        if ([regex]::Matches($text, $topPattern).Count -ne 1) { throw 'Recents scene top margin site not uniquely found.' }
        $text = [regex]::Replace($text, $topPattern, ('$0' + @'

    move-object/from16 v44, p1
    move/from16 v45, v25
    invoke-static/range {v44 .. v45}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forSceneTopMargin(Landroid/content/Context;I)I
    move-result v45
    move/16 v25, v45
'@), 1)
        $text
    }
    # Keep Recents and task-snapshot insets aligned while the launcher package
    # itself is exempt from the package-level immersive policy. PackageManager
    # reads the stock manifest outside the app's private mount namespace, so
    # per-activity fullscreen must be applied by the activity lifecycle code.
    $compatPath = Join-Path $DecodedRoot 'smali_classes6/com/nemotron/oneui/compat'
    $statusBarPath = Join-Path $compatPath 'RecentsStatusBar.smali'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/HomeOnlyStatusBar.smali') -Destination (Join-Path $compatPath 'HomeOnlyStatusBar.smali') -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/HomeOnlyStatusBar$1.smali') -Destination (Join-Path $compatPath 'HomeOnlyStatusBar$1.smali') -Force
    $statusBarSmali = @'
.class public final Lcom/nemotron/oneui/compat/RecentsStatusBar;
.super Ljava/lang/Object;

.method public static hideIfImmersiveEnabled(Landroid/app/Activity;)V
    .locals 4

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    const-string v1, "policy_control"
    invoke-static {v0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :done

    # Recents belongs to One UI Home, whose normal home screen is the only
    # state exempted from the global immersive status-bar rule. Revoke that
    # exemption as soon as Recents receives the gesture, before a task card
    # starts its horizontal launch transition.
    const-string v1, "immersive.status=apps,-com.sec.android.app.launcher"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :nemotron_policy_ready
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v2
    const-string v1, "policy_control"
    const-string v3, "immersive.status=apps"
    invoke-static {v2, v1, v3}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z
    :nemotron_policy_ready

    const-string v1, "immersive.status=apps"
    invoke-virtual {v0, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v0
    if-eqz v0, :done

    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
    move-result-object v0
    const/16 v1, 0x400
    invoke-virtual {v0, v1}, Landroid/view/Window;->addFlags(I)V
    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
    move-result-object v0
    const/16 v1, 0x1504
    invoke-virtual {v0, v1}, Landroid/view/View;->setSystemUiVisibility(I)V

    :done
    return-void
.end method
'@
    New-Item -ItemType Directory -Path $compatPath -Force | Out-Null
    [IO.File]::WriteAllText($statusBarPath, $statusBarSmali, [Text.UTF8Encoding]::new($false))

    Edit-Class 'RecentsActivity.smali' {
        param($text)
        $resume = '(?s)(\.method public final onResume\(\)V.*?invoke-super \{p0\}, L[^;]+;->onResume\(\)V\r?\n)'
        if ([regex]::Matches($text, $resume).Count -ne 1) { throw 'Recents background hook was not unique.' }
        $text = [regex]::Replace($text, $resume, ('$0' + @'
    const v0, 0x7f0a0554
    invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
    move-result-object v0
    if-eqz v0, :nemotron_recents_black_done
    const/high16 v1, -0x1000000
    invoke-virtual {v0, v1}, Landroid/view/View;->setBackgroundColor(I)V
    :nemotron_recents_black_done
    invoke-static {p0}, Lcom/nemotron/oneui/compat/RecentsStatusBar;->hideIfImmersiveEnabled(Landroid/app/Activity;)V
'@ + "`n"), 1)
        $pattern = '(?s)(\.method public final onCreate\(Landroid/os/Bundle;\)V.*?)(    invoke-super/range \{p0 \.\. p1\}, L[^;]+;->onCreate\(Landroid/os/Bundle;\)V)'
        $match = [regex]::Match($text, $pattern)
        if (-not $match.Success) { throw 'RecentsActivity onCreate not found.' }
        $needle = $match.Groups[2].Value
        # onCreate has 17 locals, so p0 aliases v17 and cannot be used by a
        # non-range invoke. v0 already holds this Activity in Samsung's method.
        $insertion = $needle + "`n    invoke-static {v0}, Lcom/nemotron/oneui/compat/RecentsStatusBar;->hideIfImmersiveEnabled(Landroid/app/Activity;)V`n"
        $text.Replace($needle, $insertion)
    }
    Edit-Class 'RecentsActivity.smali' {
        param($text)
        if ($text -match '(?m)^\.method public onUserInteraction\(\)V') { throw 'RecentsActivity already overrides onUserInteraction; patch it explicitly.' }
        $text + @'

.method public onUserInteraction()V
    .locals 0

    invoke-super {p0}, Landroidx/fragment/app/FragmentActivity;->onUserInteraction()V
    invoke-static {p0}, Lcom/nemotron/oneui/compat/RecentsStatusBar;->hideIfImmersiveEnabled(Landroid/app/Activity;)V
    return-void
.end method
'@
    }
    Edit-Class 'RecentsActivity.smali' {
        param($text)
        if ($text -match '(?m)^\.method public onWindowFocusChanged\(Z\)V') { throw 'RecentsActivity already overrides onWindowFocusChanged; patch the existing method explicitly.' }
        $text + @'

.method public onWindowFocusChanged(Z)V
    .locals 0

    invoke-super {p0, p1}, LL/a;->onWindowFocusChanged(Z)V
    if-eqz p1, :focus_done
    invoke-static {p0}, Lcom/nemotron/oneui/compat/RecentsStatusBar;->hideIfImmersiveEnabled(Landroid/app/Activity;)V
    :focus_done
    return-void
.end method
'@
    }
    # The package-level immersive rule includes One UI Home. Restore its status
    # bar as soon as the Home transition completes, without waiting for the
    # remaining launcher animation frames. External Quick Switch gestures hold
    # a guard until Home regains window focus.
    Edit-Class 'Launcher.smali' {
        param($text)
        $resume = '(?s)(\.method public final onResume\(\)V.*?invoke-super \{p0\}, L(?:com/nemotron/oneui/shaded/)?androidx/fragment/app/FragmentActivity;->onResume\(\)V\r?\n)'
        if ([regex]::Matches($text, $resume).Count -ne 1) { throw 'Launcher.onResume hook was not unique.' }
        $text = [regex]::Replace($text, $resume, ('$0' + "    invoke-static {p0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->attachAndApply(Lcom/sec/android/app/launcher/Launcher;)V`n"), 1)

        $pause = '(?s)(\.method public final onPause\(\)V.*?invoke-super \{p0\}, L(?:com/nemotron/oneui/shaded/)?androidx/fragment/app/FragmentActivity;->onPause\(\)V\r?\n)'
        if ([regex]::Matches($text, $pause).Count -ne 1) { throw 'Launcher.onPause hook was not unique.' }
        $text = [regex]::Replace($text, $pause, ('$0' + "    invoke-static {p0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->detach(Lcom/sec/android/app/launcher/Launcher;)V`n"), 1)

        $focus = '(?s)(\.method public final onWindowFocusChanged\(Z\)V.*?invoke-super \{p0, p1\}, Landroid/app/Activity;->onWindowFocusChanged\(Z\)V\r?\n)'
        if ([regex]::Matches($text, $focus).Count -ne 1) { throw 'Launcher.onWindowFocusChanged hook was not unique.' }
        $focusHook = '$0' + @'
    if-eqz p1, :nemotron_home_focus_done
    invoke-static {p0}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->onHomeWindowFocusChanged(Lcom/sec/android/app/launcher/Launcher;)V
    :nemotron_home_focus_done
'@
        $text = [regex]::Replace($text, $focus, $focusHook, 1)
        $text
    }
    # HoneyScreenManager is the authoritative source for Home/All Apps state.
    # Notify the currently resumed Launcher whenever its transition flag moves,
    # so a swipe never leaves a temporary status bar visible over the drawer.
    Edit-RelativePath 'smali_classes3/h3.1/i.smali' {
        param($text)
        $stateWrite = '(?m)^([ \t]*)iput-boolean (?:v\d+|p\d+), (p0|v\d+), Lh3/i;->q:Z\r?$'
        if ([regex]::Matches($text, $stateWrite).Count -lt 5) { throw 'HoneyScreenManager transition sites changed; refusing to patch.' }
        [regex]::Replace($text, $stateWrite, ('$0' + "`n`$1invoke-static {`$2}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->onStateChanged(Lcom/honeyspace/sdk/HoneyScreenManager;)V"))
    }
    # The gesture destination is known before Home regains window focus. Switch
    # only the Launcher exception at that point so the bar is ready as Home
    # enters; other Recents/app destinations keep the immersive policy.
    Edit-RelativePath 'smali_classes3/com/honeyspace/gesture/recentsanimation/RecentsAnimationManager.smali' {
        param($text)
        if ($text.Contains('onHomeGestureTargetChanged')) { return $text }
        $method = [regex]::Match($text, '(?s)\.method private final setEndTarget\(Lcom/honeyspace/gesture/recentsanimation/GestureEndTarget;\)V.*?\.end method')
        if (-not $method.Success) { throw 'RecentsAnimationManager.setEndTarget was not found.' }
        $block = $method.Value
        if ($block -notmatch '(?m)^    \.locals 3\r?$') { throw 'RecentsAnimationManager.setEndTarget locals changed.' }
        $block = [regex]::Replace($block, '(?m)^    \.locals 3\r?$', '    .locals 5', 1)
        $setter = '(?m)^([ \t]*)invoke-interface \{[^}]+\}, L(?:com/nemotron/oneui/shaded/)?kotlin/properties/ReadWriteProperty;->setValue\(Ljava/lang/Object;L(?:com/nemotron/oneui/shaded/)?kotlin/reflect/KProperty;Ljava/lang/Object;\)V\r?$'
        if ([regex]::Matches($block, $setter).Count -ne 1) { throw 'RecentsAnimationManager end-target setter site was not unique.' }
        $hook = @'
    iget-object v0, p0, Lcom/honeyspace/gesture/recentsanimation/RecentsAnimationManager;->context:Landroid/content/Context;
    if-eqz p1, :nemotron_home_target_null
    invoke-virtual {p1}, Lcom/honeyspace/gesture/recentsanimation/GestureEndTarget;->isHome()Z
    move-result v1
    invoke-static {v0, v1}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->onHomeGestureTargetChanged(Landroid/content/Context;Z)V
    :nemotron_home_target_null
'@
        $block = [regex]::Replace($block, $setter, ('$0' + "`n" + $hook), 1)
        $text.Remove($method.Index, $method.Length).Insert($method.Index, $block)
    }
    # The horizontal Quick Switch gesture starts in the foreground task and is
    # delivered to OtherActivityInputConsumer before RecentsActivity exists.
    # Revoke Home's temporary package exemption on ACTION_DOWN, before the
    # lateral task animation can expose a status bar or reserve its top inset.
    Edit-RelativePath 'smali_classes3/com/honeyspace/gesture/inputconsumer/OtherActivityInputConsumer.smali' {
        param($text)
        if ($text.Contains(':nemotron_quick_switch_hide_status_bar')) { return $text }
        $site = '(?s)(\.method public onMotionEvent\(Landroid/view/MotionEvent;\)V.*?invoke-virtual \{p1\}, Landroid/view/MotionEvent;->getActionMasked\(\)I\r?\n\s*move-result v0\r?\n)'
        if ([regex]::Matches($text, $site).Count -ne 1) { throw 'Quick Switch motion-event hook was not unique.' }
        $hook = @'
    if-nez v0, :nemotron_quick_switch_hide_status_bar
    iget-object v1, p0, Lcom/honeyspace/gesture/inputconsumer/OtherActivityInputConsumer;->context:Landroid/content/Context;
    invoke-static {v1}, Lcom/nemotron/oneui/compat/HomeOnlyStatusBar;->onExternalAppGestureStart(Landroid/content/Context;)V
    :nemotron_quick_switch_hide_status_bar
'@
        [regex]::Replace($text, $site, ('$1' + $hook), 1)
    }
    # A downward swipe to fullscreen uses LaunchTaskHelper rather than the
    # tapped-card TaskListViewModel path. Suppress Samsung's stale starting
    # window for immersive task launches.
    Edit-Class 'LaunchTaskHelper.smali' {
        param($text)
        if ($text.Contains(':nemotron_gesture_keep_starting_window')) { return $text }
        $pattern = '    invoke-interface \{v0, p1\}, Lcom/honeyspace/sdk/HoneySystemController;->makeShellTransitionOptions\(Lcom/honeyspace/sdk/transition/ShellTransition\$Info;\)Landroid/app/ActivityOptions;\s+move-result-object v4'
        if ([regex]::Matches($text, $pattern).Count -ne 1) { throw 'Gesture task launch options were not unique in LaunchTaskHelper.' }
        $insertion = '$0' + @'

    invoke-virtual {p1}, Lcom/honeyspace/sdk/transition/ShellTransition$Info;->getTargetView()Landroid/view/View;
    move-result-object v0
    invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    const-string v1, "policy_control"
    invoke-static {v0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :nemotron_gesture_keep_starting_window
    const-string v1, "immersive.status=apps"
    invoke-virtual {v1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-eqz v1, :nemotron_gesture_keep_starting_window
    iget-object v0, v3, Lcom/android/systemui/shared/recents/model/Task;->topActivity:Landroid/content/ComponentName;
    if-eqz v0, :nemotron_gesture_keep_starting_window
    const/4 v0, 0x1
    invoke-static {v4, v0}, Lcom/android/systemui/shared/launcher/ActivityOptionsCompat;->setDisableStartingWindow(Landroid/app/ActivityOptions;Z)V
    :nemotron_gesture_keep_starting_window
'@
        [regex]::Replace($text, $pattern, $insertion, 1)
    }
    # Normalize each task's top content inset against Android's runtime status-
    # bar height in ImmersiveRecentsInsets. This retains app-specific safe-area
    # remainder and leaves the task's native width/height ratio unchanged.
    Edit-Class 'PlayerImpl.smali' {
        param($text)
        if (-not $text.Contains('# nemotron_immersive_target_content_insets')) {
            $methodPattern = '(?s)(\.method private final calculateInset\(\)V\s+)\.locals 9(\s+)'
            if ([regex]::Matches($text, $methodPattern).Count -ne 1) { throw 'PlayerImpl.calculateInset locals were not unique.' }
            $text = [regex]::Replace($text, $methodPattern, '$1.locals 10$2    move-object/from16 v9, p0' + "`n    iget-object v9, v9, Lcom/honeyspace/transition/anim/floating/PlayerImpl;->context:Landroid/content/Context;`n", 1)
            $insetCall = '    invoke-virtual \{v3, p0\}, Lcom/honeyspace/transition/anim/floating/entity/FloatingOutputData;->setContentsInsets\(Landroid/graphics/RectF;\)V'
            if ([regex]::Matches($text, $insetCall).Count -ne 2) { throw 'PlayerImpl content-inset output sites were not exactly two.' }
            $insertion = @'
    # nemotron_immersive_target_content_insets
    invoke-static/range {v9 .. p0}, Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;->forImmersiveContent(Landroid/content/Context;Landroid/graphics/RectF;)Landroid/graphics/RectF;
    move-result-object p0
    invoke-virtual {v3, p0}, Lcom/honeyspace/transition/anim/floating/entity/FloatingOutputData;->setContentsInsets(Landroid/graphics/RectF;)V
'@
            $text = [regex]::Replace($text, $insetCall, $insertion.TrimEnd(), 2)
        }
        $text
    }
}
# A wrapper shortcut's publisher is Nemotron, but its running task belongs to
# the destination app. Samsung otherwise only matches AppItem/package pairs.
Edit-Class 'CloseTarget$DefaultImpls.smali' {
    param($text)
    if ($text.Contains(':nemotron_original_match')) { return $text }
    [regex]::Replace($text, '(?m)(\.method private static isPackageAndUserMatchedItem\([^\r\n]+\r?\n\s*\.locals \d+\r?\n)', '$1' + @'

    invoke-static {p1, p2}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->matches(Lcom/honeyspace/sdk/source/entity/BaseItem;Lcom/honeyspace/sdk/transition/CloseTarget$Key;)Z
    move-result v0
    if-eqz v0, :nemotron_original_match
    return v0
    :nemotron_original_match
'@)
}
$closeAdapters = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' | Where-Object { $_.Name -in @('A.smali','P0.smali','Z0.smali','r0.1.smali') }
foreach ($adapter in $closeAdapters) {
    $code = [IO.File]::ReadAllText($adapter.FullName)
    $pattern = 'new-instance p0, Lcom/honeyspace/sdk/transition/CloseTarget\$Value;\s+instance-of p1, p1, L[^;]+;\s+invoke-direct \{p0, (\w+), (\w+), p1\}, Lcom/honeyspace/sdk/transition/CloseTarget\$Value;-><init>\(Landroid/view/View;Landroid/content/ComponentName;Z\)V'
    if ($code -match $pattern) {
        $code = [regex]::Replace($code, $pattern, {
            param($m)
            'invoke-static {' + $m.Groups[1].Value + ', ' + $m.Groups[2].Value + ', p1}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->value(Landroid/view/View;Landroid/content/ComponentName;Lcom/honeyspace/sdk/source/entity/ModelItemSupplier;)Lcom/honeyspace/sdk/transition/CloseTarget$Value;' + "`n    move-result-object p0"
        })
        [IO.File]::WriteAllText($adapter.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
}
# Manual addition uses the public pinned-shortcut flow; installation never
# authorizes an automatic native AppItem placement.
$androidNs = 'http://schemas.android.com/apk/res/android'
$settingsPath = Join-Path $DecodedRoot 'res/xml/settings_preferences.xml'
[xml]$settingsXml = Get-Content -LiteralPath $settingsPath -Raw
$manual = $settingsXml.SelectNodes('//*') | Where-Object { $_.GetAttribute('key', $androidNs) -eq 'nemotron_add_apps' } | Select-Object -First 1
if (-not $manual) {
    $manual = $settingsXml.CreateElement('Preference')
    [void]$manual.SetAttribute('key', $androidNs, 'nemotron_add_apps')
    [void]$manual.SetAttribute('title', $androidNs, 'Añadir aplicaciones')
    [void]$manual.SetAttribute('summary', $androidNs, 'Elige qué aplicaciones aparecen en Inicio. Las nuevas no se añaden automáticamente.')
    $intent = $settingsXml.CreateElement('intent')
    [void]$intent.SetAttribute('action', $androidNs, 'android.intent.action.MAIN')
    [void]$intent.SetAttribute('targetPackage', $androidNs, 'com.nemotron.voiceime2')
    [void]$intent.SetAttribute('targetClass', $androidNs, 'com.nemotron.voiceime.ui.ShortcutPickerActivity')
    [void]$manual.AppendChild($intent)
    [void]$settingsXml.DocumentElement.PrependChild($manual)
    $settingsXml.Save($settingsPath)
}
if (-not $StockHost) { Edit-Class 'ShortcutPickerActivity.smali' {
    param($text)
    $obsoleteLoad = @'
    invoke-static {p0}, Lcom/nemotron/oneui/compat/InstalledApps;->load(Landroid/content/Context;)Ljava/util/List;
    move-result-object v0
    iput-object v0, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;
'@
    $text = $text.Replace($obsoleteLoad.Trim(), '')
    # Kotlin's original onCreate rebuilds the list after super.onCreate. Replace
    # that final assignment so the filtered native list cannot overwrite ours.
    if (-not $text.Contains('# nemotron_manual_picker_list')) {
        $assignment = '    iput-object v2, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;'
        if (-not $text.Contains($assignment)) { throw 'ShortcutPickerActivity app-list assignment was not found.' }
        $replacement = @'
    # nemotron_manual_picker_list
    invoke-static {p0}, Lcom/nemotron/oneui/compat/InstalledApps;->load(Landroid/content/Context;)Ljava/util/List;
    move-result-object v2
    iput-object v2, p0, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->apps:Ljava/util/List;
'@
        $text = $text.Replace($assignment, $replacement.TrimEnd())
    }
    $text = [regex]::Replace($text, '(?ms)^\.method private final requestShortcut\(Lcom/nemotron/voiceime/ui/ShortcutPickerActivity\$AppInfo;\)V.*?^\.end method', @'
.method private final requestShortcut(Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;)V
    .locals 12
    invoke-direct {p0, p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->shortcutDrawable(Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;)Landroid/graphics/drawable/Drawable;
    move-result-object v0
    invoke-direct {p0, v0}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity;->renderToBitmap(Landroid/graphics/drawable/Drawable;)Landroid/graphics/Bitmap;
    move-result-object v1
    invoke-static {v1}, Landroidx/core/graphics/drawable/IconCompat;->createWithBitmap(Landroid/graphics/Bitmap;)Landroidx/core/graphics/drawable/IconCompat;
    move-result-object v2
    invoke-virtual {p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;->getPkg()Ljava/lang/String;
    move-result-object v3
    new-instance v4, Ljava/lang/StringBuilder;
    const-string v5, "nemotron_app_"
    invoke-direct {v4, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v4
    const-string v5, "_"
    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v4
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v7
    invoke-virtual {v4, v7, v8}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;
    move-result-object v4
    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v4
    new-instance v6, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    invoke-direct {v6, p0, v4}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;-><init>(Landroid/content/Context;Ljava/lang/String;)V
    invoke-virtual {p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;->getLabel()Ljava/lang/String;
    move-result-object v5
    invoke-virtual {v6, v5}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setShortLabel(Ljava/lang/CharSequence;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    invoke-virtual {v6, v5}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setLongLabel(Ljava/lang/CharSequence;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    invoke-virtual {v6, v2}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setIcon(Landroidx/core/graphics/drawable/IconCompat;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    new-instance v9, Landroid/content/Intent;
    const-string v5, "android.intent.action.MAIN"
    invoke-direct {v9, v5}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
    const-string v5, "android.intent.category.LAUNCHER"
    invoke-virtual {v9, v5}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;
    invoke-virtual {p1}, Lcom/nemotron/voiceime/ui/ShortcutPickerActivity$AppInfo;->getActivity()Ljava/lang/String;
    move-result-object v5
    new-instance v10, Landroid/content/ComponentName;
    invoke-direct {v10, v3, v5}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v9, v10}, Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;
    const v5, 0x10200000
    invoke-virtual {v9, v5}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;
    invoke-virtual {v6, v9}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->setIntent(Landroid/content/Intent;)Landroidx/core/content/pm/ShortcutInfoCompat$Builder;
    move-result-object v6
    invoke-virtual {v6}, Landroidx/core/content/pm/ShortcutInfoCompat$Builder;->build()Landroidx/core/content/pm/ShortcutInfoCompat;
    move-result-object v11
    const/4 v5, 0x0
    invoke-static {p0, v11, v5}, Landroidx/core/content/pm/ShortcutManagerCompat;->requestPinShortcut(Landroid/content/Context;Landroidx/core/content/pm/ShortcutInfoCompat;Landroid/content/IntentSender;)Z
    move-result v5
    if-eqz v5, :nemotron_pin_failed
    invoke-static {p0, v3}, Lcom/nemotron/oneui/compat/InstalledApps;->selectManually(Landroid/content/Context;Ljava/lang/String;)V
    const-string v4, "Confirma anadir la app a Inicio"
    goto :nemotron_pin_toast
    :nemotron_pin_failed
    const-string v4, "No se pudo solicitar el icono a Inicio"
    :nemotron_pin_toast
    const/4 v5, 0x0
    invoke-static {p0, v4, v5}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v0
    invoke-virtual {v0}, Landroid/widget/Toast;->show()V
    return-void
.end method
'@)
    if (-not $text.Contains('ShortcutManagerCompat;->requestPinShortcut')) {
        throw 'ShortcutPickerActivity must request a real Home pin; refusing to build a toast-only picker.'
    }
    $text
}
}
$pickerLayout = Join-Path $DecodedRoot 'res/layout/activity_shortcut_picker.xml'
if (Test-Path -LiteralPath $pickerLayout) {
    $pickerText = [IO.File]::ReadAllText($pickerLayout)
    $pickerText = $pickerText.Replace('App shortcuts', 'Añadir aplicaciones').Replace('Press + on an app to pin its shortcut to home with its native icon.', 'Pulsa + para añadir una aplicación a Inicio. Las apps nuevas no se añaden automáticamente.')
    [IO.File]::WriteAllText($pickerLayout, $pickerText, [Text.UTF8Encoding]::new($false))
}
# Apply the persistent policy at the database boundary, including cold startup.
$settingsFragment = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'SettingsFragment.smali' | Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.class public final Lcom/android/homescreen/settings/SettingsFragment;') } | Select-Object -First 1
if (-not $settingsFragment) { throw 'Native Home SettingsFragment missing' }
$settingsCode = [IO.File]::ReadAllText($settingsFragment.FullName)
if (-not $settingsCode.Contains(':nemotron_native_setting')) {
    $settingsCode = [regex]::Replace($settingsCode, '(?m)(\.method public static f\(Lcom/nemotron/oneui/shaded/androidx/preference/Preference;Lcom/android/homescreen/settings/SettingsFragment;\)Lcom/nemotron/oneui/shaded/kotlin/Unit;\r?\n\s*\.locals \d+\r?\n)', '$1' + @'

    move-object/from16 v0, p0
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->getKey()Ljava/lang/String;
    move-result-object v1
    const-string v2, "nemotron_add_apps"
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :nemotron_native_setting
    const/4 v1, 0x1
    invoke-virtual {v0, v1}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->setVisible(Z)V
    new-instance v1, Lcom/nemotron/oneui/compat/AddAppsPreferenceClick;
    invoke-direct {v1}, Lcom/nemotron/oneui/compat/AddAppsPreferenceClick;-><init>()V
    invoke-virtual {v0, v1}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->setOnPreferenceClickListener(Lcom/nemotron/oneui/shaded/androidx/preference/Preference$OnPreferenceClickListener;)V
    sget-object v0, Lcom/nemotron/oneui/shaded/kotlin/Unit;->INSTANCE:Lcom/nemotron/oneui/shaded/kotlin/Unit;
    return-object v0
    :nemotron_native_setting
'@)
    [IO.File]::WriteAllText($settingsFragment.FullName, $settingsCode, [Text.UTF8Encoding]::new($false))
}
# RxJava 3's IO scheduler wakes at its keep-alive interval to evict cached
# workers. Keep normal cleanup, but reduce this idle maintenance from once a
# minute to once every five minutes. Explicit system-property overrides remain
# respected; idle IO workers are retained for up to five minutes instead.
$rxIoKeepAlive = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' |
    Where-Object {
        $code = [IO.File]::ReadAllText($_.FullName)
        $code.Contains('rx3.io-keep-alive-time') -and $code.Contains('Ljava/lang/Long;->getLong')
    } | Select-Object -First 1
if ($rxIoKeepAlive) {
    $rxIoCode = [IO.File]::ReadAllText($rxIoKeepAlive.FullName)
    if (-not $rxIoCode.Contains('nemotron_rx3_io_eviction_300s')) {
        $rxIoPattern = '(?m)(const-string/jumbo v0, "rx3\.io-keep-alive-time"\r?\n\s*const-wide/16 v1, )0x3c(\r?\n\s*invoke-static \{v0, v1, v2\}, Ljava/lang/Long;->getLong)'
        $rxIoMatches = [regex]::Matches($rxIoCode, $rxIoPattern)
        if ($rxIoMatches.Count -ne 1) { throw 'RxJava 3 IO keep-alive default changed; refusing to patch it.' }
        $rxIoCode = [regex]::Replace($rxIoCode, $rxIoPattern, {
            param($match)
            $match.Groups[1].Value + '0x12c # nemotron_rx3_io_eviction_300s' + $match.Groups[2].Value
        }, 1)
        [IO.File]::WriteAllText($rxIoKeepAlive.FullName, $rxIoCode, [Text.UTF8Encoding]::new($false))
    }
} else {
    Write-Verbose 'This One UI Home build has no RxJava 3 IO scheduler to tune.'
}

# RxJava 2 builds also use a global purge executor that scans cancelled tasks.
# Keep cleanup enabled, but run it every five minutes; delayed tasks can remain
# queued for at most 300 s in those builds.
$rxPurge = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' |
    Where-Object {
        $code = [IO.File]::ReadAllText($_.FullName)
        $code.Contains('rx2.purge-period-seconds') -and $code.Contains('Lrv0/k$a;->b:I')
    } | Select-Object -First 1
if ($rxPurge) {
    $rxCode = [IO.File]::ReadAllText($rxPurge.FullName)
    if (-not $rxCode.Contains('nemotron_rx_purge_300s')) {
        $rxPattern = '(?m)(^\s*:cond_1\s*\r?\n)(\s*)(iput v2, p0, Lrv0/k\$a;->b:I)'
        $matches = [regex]::Matches($rxCode, $rxPattern)
        if ($matches.Count -ne 1) { throw 'RxJava purge default layout changed; refusing to patch it.' }
        $rxCode = [regex]::Replace($rxCode, $rxPattern, {
            param($match)
            $newline = if ($match.Value.Contains("`r`n")) { "`r`n" } else { "`n" }
            $match.Groups[1].Value + $match.Groups[2].Value + 'const/16 v2, 0x12c' + $newline +
                $match.Groups[2].Value + $match.Groups[3].Value + ' # nemotron_rx_purge_300s'
        }, 1)
        [IO.File]::WriteAllText($rxPurge.FullName, $rxCode, [Text.UTF8Encoding]::new($false))
    }
} else {
    Write-Verbose 'This One UI Home build has no RxJava2 purge worker to tune.'
}
$delegate = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter '*.smali' | Where-Object { $_.Name -eq 'B.smali' } | Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('.field public final synthetic a:Lcom/honeyspace/data/db/SpaceDB_Impl;') } | Select-Object -First 1
if (-not $delegate) { throw 'SpaceDB Room delegate missing' }
$delegateCode = [IO.File]::ReadAllText($delegate.FullName)
if (-not $delegateCode.Contains('Lcom/nemotron/oneui/compat/HomeOnlyPolicy;->apply')) {
    $delegateCode = [regex]::Replace($delegateCode, '(?m)(\.method public final onOpen\([^\r\n]+\r?\n\s*\.locals \d+\r?\n)', '$1' + "`n    invoke-static {p1}, Lcom/nemotron/oneui/compat/HomeOnlyPolicy;->apply(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;)V`n")
    [IO.File]::WriteAllText($delegate.FullName, $delegateCode, [Text.UTF8Encoding]::new($false))
}
$compat = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'LauncherCompat.smali' | Select-Object -First 1
if (-not $compat) { throw 'Launcher compatibility classes missing' }
$compatDir = $compat.DirectoryName
$installedAppsSource = if ($StockHost) { Join-Path $PSScriptRoot 'oneui-compat/InstalledApps-stock.smali' } else { Join-Path $PSScriptRoot 'oneui-compat/InstalledApps.smali' }
Copy-Item -LiteralPath $installedAppsSource -Destination (Join-Path $compatDir 'InstalledApps.smali') -Force
if ($StockHost) {
    foreach ($helper in @('ShortcutCloseTarget.smali', 'NoAutoAdd.smali')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "oneui-compat/$helper") -Destination (Join-Path $compatDir $helper) -Force
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/AddAppsPreferenceClick-stock.smali') -Destination (Join-Path $compatDir 'AddAppsPreferenceClick.smali') -Force
}
$policy = @'
.class public final Lcom/nemotron/oneui/compat/HomeOnlyPolicy;
.super Ljava/lang/Object;
.method public static apply(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;)V
    .locals 4
    const-string v0, "PRAGMA database_list"
    invoke-interface {p0, v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;->prepare(Ljava/lang/String;)Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;
    move-result-object v0
    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->step()Z
    move-result v1
    if-eqz v1, :close
    const/4 v1, 0x2
    invoke-interface {v0, v1}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->getText(I)Ljava/lang/String;
    move-result-object v1
    const-string v2, "HomeOnly.db"
    invoke-virtual {v1, v2}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
    move-result v3
    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->close()V
    if-nez v3, :apply_policy
    const-string v2, "OneUI.db"
    invoke-virtual {v1, v2}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
    move-result v3
    if-eqz v3, :done
    :apply_policy
'@
 $sqlBuffer = [System.Text.StringBuilder]::new()
 $sqlStatements = [System.Collections.Generic.List[string]]::new()
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'oneui-compat/home-only-policy.sql')) {
    $line = $line.Trim()
    if (-not $line -or $line.StartsWith('--')) { continue }
    [void]$sqlBuffer.Append($line).Append(' ')
    while (($sqlText = $sqlBuffer.ToString()).Contains(';')) {
        $end = $sqlText.IndexOf(';')
        $sqlStatements.Add($sqlText.Substring(0, $end + 1).Trim())
        [void]$sqlBuffer.Clear()
        [void]$sqlBuffer.Append($sqlText.Substring($end + 1))
    }
}
if ($sqlBuffer.ToString().Trim()) { throw 'Home-only policy SQL contains an unterminated statement.' }
foreach ($sql in $sqlStatements) {
    $escaped = $sql.Replace('\','\\').Replace('"','\"')
    $policy += "`n    const-string v0, `"$escaped`"`n    invoke-static {p0, v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLite;->execSQL(Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteConnection;Ljava/lang/String;)V`n"
}
$policy += "`n    goto :done`n    :close`n    invoke-interface {v0}, Lcom/nemotron/oneui/shaded/androidx/sqlite/SQLiteStatement;->close()V`n    :done`n    return-void`n.end method`n"
[IO.File]::WriteAllText((Join-Path $compatDir 'HomeOnlyPolicy.smali'), $policy, [Text.UTF8Encoding]::new($false))
if (-not $StockHost) { Edit-Class 'SearchBackupReceiver.smali' {
    param($text)
    $text.Replace('const-string v1, "com.sec.android.app.launcher"', 'const-string v1, "com.nemotron.voiceime2"')
} }
Edit-Class 'WorkspaceViewModel.smali' {
    param($text)
    $text = [regex]::Replace($text, '(?ms)^\.method public final u\(Lcom/nemotron/oneui/shaded/y8/d0;Z\)V.*?^\.end method', {
        param($m)
        $method = $m.Value
        # Keep automatically discovered apps off Home, but allow an AppItem
        # explicitly chosen in Nemotron's app selector to use One UI's model.
        if (-not $method.Contains(':nemotron_manual_item_v2')) {
        $method = [regex]::Replace($method, '(?m)(^\s*\.locals \d+\r?\n)', '$1' + @'

    move-object/from16 v0, p1
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/y8/d0;->getItem()Lcom/honeyspace/sdk/source/entity/BaseItem;
    move-result-object v0
    instance-of v0, v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    if-eqz v0, :nemotron_manual_item_v2
    move-object/from16 v0, p1
    invoke-virtual {v0}, Lcom/nemotron/oneui/shaded/y8/d0;->getItem()Lcom/honeyspace/sdk/source/entity/BaseItem;
    move-result-object v0
    check-cast v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/AppItem;->getComponent()Lcom/honeyspace/sdk/source/entity/ComponentKey;
    move-result-object v0
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/ComponentKey;->getPackageName()Ljava/lang/String;
    move-result-object v1
    move-object/from16 v3, p0
    iget-object v2, v3, Lcom/honeyspace/ui/honeypots/workspace/viewmodel/WorkspaceViewModel;->e:Landroid/content/Context;
    invoke-static {v2, v1}, Lcom/nemotron/oneui/compat/InstalledApps;->isManuallySelected(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :nemotron_manual_item_v2
    return-void
    :nemotron_manual_item_v2
'@)
        }
        $method
    })
    [regex]::Replace($text, '(?ms)^\.method public final s\(\)Z.*?^\.end method', @'
.method public final s()Z
    .locals 1
    const/4 v0, 0x0
    return v0
.end method
'@)
}
Edit-Class 'VerticalApplistViewModel.smali' {
    param($text)
    [regex]::Replace($text, '(?ms)^\.method public final f\(Lcom/nemotron/oneui/shaded/h8/d;\)V.*?^\.end method', {
        param($m)
        if ($m.Value.Contains(':nemotron_hide_social_icon')) { return $m.Value }
        [regex]::Replace($m.Value, '(?m)^\s*\.locals 1\s*$', @'
    .locals 2
    invoke-virtual {p1}, Lcom/nemotron/oneui/shaded/h8/d;->d()Lcom/honeyspace/sdk/source/entity/IconItem;
    move-result-object v0
    instance-of v1, v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    if-eqz v1, :nemotron_social_visible
    check-cast v0, Lcom/honeyspace/sdk/source/entity/AppItem;
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/AppItem;->getComponent()Lcom/honeyspace/sdk/source/entity/ComponentKey;
    move-result-object v0
    invoke-virtual {v0}, Lcom/honeyspace/sdk/source/entity/ComponentKey;->getPackageName()Ljava/lang/String;
    move-result-object v0
    const-string v1, "com.whatsapp"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_hide_social_icon
    const-string v1, "com.instagram.android"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-nez v1, :nemotron_hide_social_icon
    :nemotron_social_visible
    goto :nemotron_continue_social_filter
    :nemotron_hide_social_icon
    return-void
    :nemotron_continue_social_filter
'@)
    })
}
Edit-Class 'ShellTransitionManager.smali' {
    param($text)
    # Samsung's original package retains the privileged remote-animation
    # permissions. Preserve its own launch options and transition runner while
    # still applying the warm pinned-shortcut fix below.
    if (-not $StockHost) {
    # Older builds short-circuited One UI's normal pinned-shortcut launch and
    # substituted a scale-up ActivityOptions launch. That bypasses the native
    # launcher transition path; leave the original method body intact.
    $text = [regex]::Replace($text, '(?ms)(\.method public final startShellTransitionForShortcut\([^\r\n]+\r?\n\s*\.locals \d+\s+)invoke-static \{p1, p2\}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->launch\([^\r\n]+\r?\n\s*move-result v0\s+if-eqz v0, :cond_0\s+return-void\s+:cond_0\s+', '$1')
    # Remote task/snapshot transitions require signature permissions. Keep the
    # original launch routing, with Android's ordinary ActivityOptions instead.
    $text = [regex]::Replace($text, '(?ms)^(\.method public final (?:getLaunchOptions|getActivityLaunchOptions)\([^\r\n]+\r?\n).*?^\.end method', {
        param($m)
        $m.Groups[1].Value + @'
    .locals 2
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;
    move-result-object v0
    invoke-static {p1, v0}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->attachCookie(Lcom/honeyspace/sdk/transition/ShellTransition$Info;Landroid/app/ActivityOptions;)V
    new-instance v1, Lcom/honeyspace/transition/utils/ActivityOptionsWrapper;
    invoke-direct {v1, v0}, Lcom/honeyspace/transition/utils/ActivityOptionsWrapper;-><init>(Landroid/app/ActivityOptions;)V
    return-object v1
.end method
'@
    })
    }
    # Warm Nemotron-pinned app shortcuts use their original ShortcutInfo intent
    # with an opaque extra. Android then skips the stale task snapshot while
    # retaining the normal launch transition. Cold starts and other shortcuts
    # keep LauncherApps.startShortcut unchanged.
    $text = [regex]::Replace($text, '(?ms)^(\.method public final startShellTransitionForShortcut\([^\r\n]+\r?\n).*?^\.end method', {
        param($m)
        $method = $m.Value
        if ($method.Contains(':nemotron_warm_shortcut_handled')) { return $method }
        $contextPattern = '(?s)(invoke-virtual \{p1\}, Landroid/view/View;->getContext\(\)Landroid/content/Context;\s+move-result-object p1)(\s+const-class p3, Landroid/content/pm/LauncherApps;)'
        if ([regex]::Matches($method, $contextPattern).Count -ne 1) { throw 'Could not identify the pinned-shortcut launch context.' }
        $method = [regex]::Replace($method, $contextPattern, '$1' + "`n    move-object v0, p1" + '$2', 1)
        $launchNeedle = '    invoke-virtual/range {v4 .. v9}, Landroid/content/pm/LauncherApps;->startShortcut(Ljava/lang/String;Ljava/lang/String;Landroid/graphics/Rect;Landroid/os/Bundle;Landroid/os/UserHandle;)V'
        if ([regex]::Matches($method, [regex]::Escape($launchNeedle)).Count -ne 1) { throw 'LauncherApps.startShortcut site was not unique.' }
        $launchReplacement = @'
    invoke-static {v0, p0, v8}, Lcom/nemotron/oneui/compat/ShortcutCloseTarget;->launchWarmShortcut(Landroid/content/Context;Lcom/honeyspace/sdk/source/entity/ShortcutItem;Landroid/os/Bundle;)Z
    move-result v2
    if-nez v2, :nemotron_warm_shortcut_handled
    invoke-virtual/range {v4 .. v9}, Landroid/content/pm/LauncherApps;->startShortcut(Ljava/lang/String;Ljava/lang/String;Landroid/graphics/Rect;Landroid/os/Bundle;Landroid/os/UserHandle;)V
    :nemotron_warm_shortcut_handled
'@
        $method = $method.Replace($launchNeedle, $launchReplacement.TrimEnd("`r", "`n"))
        return $method
    })
    $text
}
# The following warm-launch hooks belong to the integrated Nemotron package.
# Samsung's stock host has different HoneySystemController control flow; keep
# its native app-launch implementation intact.
if (-not $StockHost) {
# Normal app icons launch through LauncherApps.startMainActivity in this
# coroutine, bypassing the pinned-shortcut hook above. Intercept warm launches
# immediately before the LauncherApps call and leave cold launches untouched.
Edit-Class 'ShellTransitionManager$startShellTransition$1$1.smali' {
    param($text)
    if ($text.Contains(':nemotron_warm_activity_launch')) { return $text }
    $directCall = '    invoke-virtual {p1, v0, v1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V'
    if ([regex]::Matches($text, [regex]::Escape($directCall)).Count -ne 1) { throw 'Direct app activity launch site was not unique.' }
    $directReplacement = @'
    invoke-virtual {v0}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v5
    invoke-static {p1, v5, v1}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->launchWarmComponent(Landroid/content/Context;Landroid/content/ComponentName;Landroid/os/Bundle;)Z
    move-result v5
    if-nez v5, :nemotron_warm_activity_launch
'@ + $directCall + "`n    :nemotron_warm_activity_launch"
    $text = $text.Replace($directCall, $directReplacement)
    $contextPattern = 'invoke-static \{p1\}, Lcom/honeyspace/common/context/ContextExtensionKt;->getHomeContext\(Landroid/content/Context;\)Landroid/content/Context;\s+move-result-object p1'
    if ([regex]::Matches($text, $contextPattern).Count -ne 1) { throw 'LauncherApps home context site was not unique.' }
    $text = [regex]::Replace($text, $contextPattern, { param($m) $m.Value + "`n    move-object v5, p1" }, 1)
    $call = '    invoke-virtual {p1, v1, v3, v0, v2}, Landroid/content/pm/LauncherApps;->startMainActivity(Landroid/content/ComponentName;Landroid/os/UserHandle;Landroid/graphics/Rect;Landroid/os/Bundle;)V'
    if ([regex]::Matches($text, [regex]::Escape($call)).Count -ne 1) { throw 'LauncherApps.startMainActivity call was not unique.' }
    $replacement = @'
    invoke-static {v5, v1, v2}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->launchWarmComponent(Landroid/content/Context;Landroid/content/ComponentName;Landroid/os/Bundle;)Z
    move-result v4
    if-nez v4, :nemotron_warm_main_activity
'@ + $call + "`n    :nemotron_warm_main_activity"
    $text = $text.Replace($call, $replacement)
    return $text
}
# Launcher icon taps use HoneySystemController.startActivity. Bring an existing
# task directly to the foreground so Android does not animate its stale snapshot
# over the live activity; disable the starting window only for a cold launch.
$honeyControllerFile = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter 'k.smali' | Where-Object {
    [IO.File]::ReadAllText($_.FullName) -match '(?m)^\.class .* LE2/k;'
} | Select-Object -First 1
if (-not $honeyControllerFile) { throw 'HoneySystemController implementation class LE2/k was not found.' }
$honeyControllerText = [IO.File]::ReadAllText($honeyControllerFile.FullName)
$needle = '    invoke-virtual {p0, p1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V'
$callIndex = $honeyControllerText.IndexOf($needle, [StringComparison]::Ordinal)
if ($callIndex -lt 0) { throw 'HoneySystemController direct Activity.startActivity call was not found.' }
$methodStart = $honeyControllerText.LastIndexOf('.method public final startActivity(', $callIndex, [StringComparison]::Ordinal)
if ($methodStart -lt 0) { throw 'HoneySystemController.startActivity method was not found.' }
$methodEnd = $honeyControllerText.IndexOf('.end method', $methodStart, [StringComparison]::Ordinal)
if ($methodEnd -lt 0) { throw 'HoneySystemController.startActivity method end was not found.' }
$methodBody = $honeyControllerText.Substring($methodStart, $methodEnd - $methodStart)
if (-not $methodBody.Contains('.locals 1')) { throw 'HoneySystemController.startActivity locals declaration was not found.' }
$methodBody = $methodBody.Replace('.locals 1', '.locals 2')
if ([regex]::Matches($methodBody, [regex]::Escape($needle)).Count -ne 1) { throw 'HoneySystemController direct startActivity call was not unique.' }
$replacement = @'
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    const-string v1, "policy_control"
    invoke-static {v0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :nemotron_standard_app_launch
    const-string v1, "immersive.status=apps"
    invoke-virtual {v1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-eqz v1, :nemotron_standard_app_launch
    const-string v1, "-com.sec.android.app.launcher"
    invoke-virtual {v1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v0
    if-eqz v0, :nemotron_standard_app_launch
    invoke-static {p0, p1}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->findTaskId(Landroid/content/Context;Landroid/content/Intent;)I
    move-result v0
    if-ltz v0, :nemotron_cold_app_launch
    const v1, 0x10000
    invoke-virtual {p1, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;
    invoke-virtual {p0, p1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    goto :nemotron_app_launch_done
    :nemotron_cold_app_launch
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;
    move-result-object v0
    const/4 v1, 0x1
    invoke-static {v0, v1}, Lcom/android/systemui/shared/launcher/ActivityOptionsCompat;->setDisableStartingWindow(Landroid/app/ActivityOptions;Z)V
    invoke-virtual {v0}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;
    move-result-object v0
    invoke-virtual {p0, p1, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V
    goto :nemotron_app_launch_done
    :nemotron_standard_app_launch
    invoke-virtual {p0, p1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    :nemotron_app_launch_done
'@
$methodBody = $methodBody.Replace($needle,$replacement)
$callbackPattern = '(?m)^    if-nez p2, :cond_\d+$'
if ([regex]::Matches($methodBody, $callbackPattern).Count -ne 1) { throw 'HoneySystemController callback launch branch was not unique.' }
$warmTaskGuard = @'
    iget-object v0, p0, LE2/k;->i:Lcom/honeyspace/sdk/HoneyActivityData;
    if-eqz v0, :nemotron_callback_launch
    invoke-virtual {v0}, Lcom/honeyspace/sdk/HoneyActivityData;->getActivity()Ljava/lang/ref/WeakReference;
    move-result-object v0
    if-eqz v0, :nemotron_callback_launch
    invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/app/Activity;
    if-eqz v0, :nemotron_callback_launch
    invoke-static {v0, p1}, Lcom/nemotron/oneui/compat/RunningTaskLaunch;->launchRunningTask(Landroid/app/Activity;Landroid/content/Intent;)Z
    move-result v1
    if-eqz v1, :nemotron_callback_launch
    return-void

    :nemotron_callback_launch
'@
$methodBody = [regex]::Replace($methodBody, $callbackPattern, {
    param($match)
    $warmTaskGuard.TrimEnd("`r", "`n") + "`r`n" + $match.Value
}, 1)
$honeyControllerText = $honeyControllerText.Substring(0,$methodStart) + $methodBody + $honeyControllerText.Substring($methodEnd)
[IO.File]::WriteAllText($honeyControllerFile.FullName, $honeyControllerText, [Text.UTF8Encoding]::new($false))
}
Edit-Class 'ContextReflection.smali' {
    param($text)
    if ($text.Contains(':own_profile')) { return $text }
    # The personal profile can use the public API; retain the old route for
    # callers explicitly targeting another profile.
    [regex]::Replace($text, '(?m)(\.method public final startActivityAsUser\(Ljava/lang/Object;Landroid/content/Intent;(Landroid/os/Bundle;)?Landroid/os/UserHandle;\)V\r?\n\s*\.locals \d+\r?\n)', {
        param($m)
        $user = if ($m.Groups[2].Success) { 'p4' } else { 'p3' }
        $call = if ($m.Groups[2].Success) { 'invoke-virtual {p1, p2, p3}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V' } else { 'invoke-virtual {p1, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V' }
        $m.Value + "`n    invoke-static {}, Landroid/os/Process;->myUserHandle()Landroid/os/UserHandle;`n    move-result-object v0`n    if-eqz $user, :own_profile`n    invoke-virtual {v0, $user}, Landroid/os/UserHandle;->equals(Ljava/lang/Object;)Z`n    move-result v0`n    if-eqz v0, :other_profile`n    :own_profile`n    check-cast p1, Landroid/content/Context;`n    $call`n    return-void`n    :other_profile`n"
    })
}
Edit-Class 'PackageEventOperator.smali' {
    param($text)
    $text = [regex]::Replace($text, '(?ms)^(\.method public final handlePackageAdded\([^\r\n]+\r?\n).*?^\.end method', {
        param($m)
        $m.Groups[1].Value + "    .locals 1`n    sget-object v0, Lcom/nemotron/oneui/shaded/kotlin/Unit;->INSTANCE:Lcom/nemotron/oneui/shaded/kotlin/Unit;`n    return-object v0`n.end method"
    })
    [regex]::Replace($text, '(?ms)^\.method public final handlePackageChanged\(.*?^\.end method', {
        param($m)
        # extraAddedOperation creates icons when a disabled package returns.
        # Preserve the update/removal callbacks for items already placed.
        $m.Value.Replace('move-object/from16 v14, p4', "new-instance v14, Lcom/nemotron/oneui/compat/NoAutoAdd;`n    invoke-direct {v14}, Lcom/nemotron/oneui/compat/NoAutoAdd;-><init>()V")
    })
}

# The integrated HoneyBoard APK schedules two idle daily jobs for Samsung
# keyboard usage statistics. Nemotron keeps HoneyBoard as its active IME, but
# does not need Samsung's periodic statistics/update tasks. Cancel existing
# jobs at startup and do not enqueue them again.
if (-not $StockHost) { foreach ($job in @(
    @{ File = 'b.smali'; Descriptor = 'Lvi/b;'; ContextGetter = 'a'; JobId = 1 },
    @{ File = 'c.smali'; Descriptor = 'Lzi/c;'; ContextGetter = 'a'; JobId = 2 }
)) {
    $classFile = Get-ChildItem -LiteralPath $DecodedRoot -Recurse -File -Filter $job.File |
        Where-Object { [regex]::IsMatch([IO.File]::ReadAllText($_.FullName), "(?m)^\.class .*" + [regex]::Escape($job.Descriptor)) } |
        Select-Object -First 1
    if (-not $classFile) { throw "Missing HoneyBoard statistics scheduler $($job.Descriptor)" }
    $code = [IO.File]::ReadAllText($classFile.FullName)
    if (-not $code.Contains(':nemotron_disable_stats_jobs')) {
        $replacement = @"
.method public onCreate()V
    .locals 3
    :nemotron_disable_stats_jobs
    invoke-super {p0}, Loo/b;->onCreate()V
    invoke-direct {p0}, $($job.Descriptor)->$($job.ContextGetter)()Landroid/content/Context;
    move-result-object v0
    const-string v1, "jobscheduler"
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    instance-of v1, v0, Landroid/app/job/JobScheduler;
    if-eqz v1, :nemotron_stats_jobs_done
    check-cast v0, Landroid/app/job/JobScheduler;
    const/4 v1, $($job.JobId)
    invoke-virtual {v0, v1}, Landroid/app/job/JobScheduler;->cancel(I)V
    :nemotron_stats_jobs_done
    return-void
.end method
"@
        $code = [regex]::Replace($code, '(?ms)^\.method public onCreate\(\)V.*?^\.end method', $replacement, 1)
        if ($code -notmatch ':nemotron_disable_stats_jobs') { throw "Could not patch $($job.Descriptor)" }
        [IO.File]::WriteAllText($classFile.FullName, $code, [Text.UTF8Encoding]::new($false))
    }
} }
