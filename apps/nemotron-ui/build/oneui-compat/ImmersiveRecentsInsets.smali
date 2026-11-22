.class public final Lcom/nemotron/oneui/compat/ImmersiveRecentsInsets;
.super Ljava/lang/Object;


# Use the same top crop in the Recents scene and transition while the status
# bar is hidden. A changing app inset must not move the card on a later visit.
.method public static forScene(Landroid/content/Context;Landroid/graphics/Insets;)Landroid/graphics/Insets;
    .locals 5

    if-eqz p0, :scene_unchanged
    if-eqz p1, :scene_unchanged

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    const-string v1, "policy_control"
    invoke-static {v0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :scene_unchanged
    const-string v1, "immersive.status=apps"
    invoke-virtual {v1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-eqz v1, :scene_unchanged
    const/4 v1, 0x0
    iget v2, p1, Landroid/graphics/Insets;->left:I
    iget v3, p1, Landroid/graphics/Insets;->right:I
    iget v4, p1, Landroid/graphics/Insets;->bottom:I
    invoke-static {v2, v1, v3, v4}, Landroid/graphics/Insets;->of(IIII)Landroid/graphics/Insets;
    move-result-object p1

    :scene_unchanged
    return-object p1
.end method

.method public static forImmersiveContent(Landroid/content/Context;Landroid/graphics/RectF;)Landroid/graphics/RectF;
    .locals 4

    if-eqz p0, :content_return
    if-eqz p1, :content_return
    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    const-string v1, "policy_control"
    invoke-static {v0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :content_return
    const-string v1, "immersive.status=apps"
    invoke-virtual {v0, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-eqz v1, :content_return
    const/4 v0, 0x0
    int-to-float v0, v0
    iput v0, p1, Landroid/graphics/RectF;->top:F

    :content_return
    return-object p1
.end method

# Use the same zero top crop for the task snapshot, Recents scene, and live
# transition while apps are immersive. Samsung may refresh a snapshot with a
# stale nonzero status-bar inset, so normalize it at every read. Copy the Rect
# and preserve the other three insets to keep cached task data untouched.
.method public static forSnapshotContentInsets(Landroid/graphics/Rect;)Landroid/graphics/Rect;
    .locals 5

    if-eqz p0, :snapshot_return

    invoke-static {}, Landroid/app/ActivityThread;->currentApplication()Landroid/app/Application;
    move-result-object v0
    if-eqz v0, :snapshot_return

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v1
    const-string v2, "policy_control"
    invoke-static {v1, v2}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    if-eqz v1, :snapshot_return
    const-string v2, "immersive.status=apps"
    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v2
    if-eqz v2, :snapshot_return
    # The immersive app window and live transition start at y=0. A refreshed
    # snapshot must not reintroduce a status-bar-height inset into its card.
    iget v1, p0, Landroid/graphics/Rect;->top:I
    if-nez v1, :snapshot_copy_rect
    goto :snapshot_return

    # Return a copy only if the top inset changes. The stored TaskSnapshot and
    # cached ThumbnailData stay untouched, so repeated reads are stable.
    :snapshot_copy_rect
    new-instance v4, Landroid/graphics/Rect;
    invoke-direct {v4, p0}, Landroid/graphics/Rect;-><init>(Landroid/graphics/Rect;)V
    const/4 v1, 0x0
    iput v1, v4, Landroid/graphics/Rect;->top:I
    move-object p0, v4

    :snapshot_return
    return-object p0
.end method

.method public static forSceneTopMargin(Landroid/content/Context;I)I
    .locals 0
    # Keep Samsung's independent label-to-card margin unchanged.
    return p1
.end method

.method public static centerScene(Landroid/content/Context;Landroid/graphics/RectF;Landroid/graphics/RectF;)Landroid/graphics/RectF;
    .locals 0
    # Preserve the app's native source rectangle and aspect ratio.
    return-object p1
.end method
