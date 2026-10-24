.class public final Lcom/nemotron/keyboard/ClipboardHistoryBridge;
.super Ljava/lang/Object;

# static fields
.field private static lastPrimarySignature:I

.method public static syncPrimaryClip(Landroid/content/Context;)V
    .locals 17
    :try_start_0
    move-object/from16 v15, p0
    const-string v0, "clipboard"
    invoke-virtual {v15, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/ClipboardManager;
    invoke-virtual {v0}, Landroid/content/ClipboardManager;->getPrimaryClip()Landroid/content/ClipData;
    move-result-object v1
    if-eqz v1, :sem_fallback
    goto :clip_ready
    :sem_fallback
    # Samsung's clipboard service can expose the copied primary item through
    # SemClipboardManager even when Android's standard primary clip is null.
    const-string v0, "semclipboard"
    invoke-virtual {v15, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/samsung/android/content/clipboard/SemClipboardManager;
    if-eqz v0, :no_primary_clip
    const/4 v2, 0x0
    invoke-virtual {v0, v2}, Lcom/samsung/android/content/clipboard/SemClipboardManager;->getLatestClip(I)Lcom/samsung/android/content/clipboard/data/SemClipData;
    move-result-object v0
    if-eqz v0, :no_primary_clip
    # SemImageClipData and SemTextClipData both expose the Android ClipData.
    # Keep the full item (including screenshot URI) so the history provider
    # can persist and render copied captures as well as text.
    invoke-virtual {v0}, Lcom/samsung/android/content/clipboard/data/SemClipData;->getClipData()Landroid/content/ClipData;
    move-result-object v1
    if-eqz v1, :no_primary_clip
    const-string v3, "NemotronClipboardBridge"
    const-string v4, "using Samsung clipboard fallback"
    invoke-static {v3, v4}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    goto :clip_ready
    :no_primary_clip
    const-string v1, "NemotronClipboardBridge"
    const-string v2, "no readable primary clipboard item"
    invoke-static {v1, v2}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    goto :done
    :clip_ready
    invoke-virtual {v1}, Landroid/content/ClipData;->getItemCount()I
    move-result v2
    if-lez v2, :done
    new-instance v3, Landroid/content/ContentValues;
    invoke-direct {v3}, Landroid/content/ContentValues;-><init>()V
    const-string v4, "caller_app_uid"
    invoke-static {}, Landroid/os/Process;->myUid()I
    move-result v5
    invoke-static {v5}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v5
    invoke-virtual {v3, v4, v5}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Integer;)V
    const-string v4, "user_id"
    const/4 v5, 0x0
    invoke-static {v5}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v6
    invoke-virtual {v3, v4, v6}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Integer;)V
    const-string v4, "time_stamp"
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v6
    invoke-static {v6, v7}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
    move-result-object v6
    invoke-virtual {v3, v4, v6}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Long;)V
    const-string v4, "locked"
    invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v6
    invoke-virtual {v3, v4, v6}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Boolean;)V
    invoke-virtual {v1, v5}, Landroid/content/ClipData;->getItemAt(I)Landroid/content/ClipData$Item;
    move-result-object v6
    invoke-virtual {v6}, Landroid/content/ClipData$Item;->getText()Ljava/lang/CharSequence;
    move-result-object v7
    if-eqz v7, :signature_uri
    invoke-interface {v7}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;
    move-result-object v7
    invoke-virtual {v7}, Ljava/lang/String;->hashCode()I
    move-result v7
    goto :check_duplicate
    :signature_uri
    invoke-virtual {v6}, Landroid/content/ClipData$Item;->getUri()Landroid/net/Uri;
    move-result-object v7
    if-eqz v7, :done
    invoke-virtual {v7}, Landroid/net/Uri;->toString()Ljava/lang/String;
    move-result-object v7
    invoke-virtual {v7}, Ljava/lang/String;->hashCode()I
    move-result v7
    :check_duplicate
    sget v8, Lcom/nemotron/keyboard/ClipboardHistoryBridge;->lastPrimarySignature:I
    if-ne v7, v8, :new_clip
    goto :done
    :new_clip
    sput v7, Lcom/nemotron/keyboard/ClipboardHistoryBridge;->lastPrimarySignature:I
    invoke-virtual {v6}, Landroid/content/ClipData$Item;->getText()Ljava/lang/CharSequence;
    move-result-object v7
    if-eqz v7, :no_text
    invoke-interface {v7}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;
    move-result-object v7
    const-string v8, "clip_text"
    invoke-virtual {v3, v8, v7}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    const-string v8, "clip_mimetypes"
    const-string v9, "text/plain"
    invoke-virtual {v3, v8, v9}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    goto :item_meta
    :no_text
    const-string v8, "clip_text"
    const-string v9, ""
    invoke-virtual {v3, v8, v9}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v1}, Landroid/content/ClipData;->getDescription()Landroid/content/ClipDescription;
    move-result-object v8
    invoke-virtual {v8, v5}, Landroid/content/ClipDescription;->getMimeType(I)Ljava/lang/String;
    move-result-object v9
    const-string v10, "clip_mimetypes"
    invoke-virtual {v3, v10, v9}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    :item_meta
    invoke-virtual {v1}, Landroid/content/ClipData;->getDescription()Landroid/content/ClipDescription;
    move-result-object v8
    invoke-virtual {v8}, Landroid/content/ClipDescription;->getLabel()Ljava/lang/CharSequence;
    move-result-object v9
    if-eqz v9, :no_label
    invoke-interface {v9}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;
    move-result-object v9
    goto :label_ready
    :no_label
    const-string v9, "Clipboard"
    :label_ready
    const-string v10, "clip_label"
    invoke-virtual {v3, v10, v9}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v6}, Landroid/content/ClipData$Item;->getUri()Landroid/net/Uri;
    move-result-object v10
    if-eqz v10, :insert_clip
    invoke-virtual {v10}, Landroid/net/Uri;->toString()Ljava/lang/String;
    move-result-object v11
    const-string v12, "clip_uri"
    invoke-virtual {v3, v12, v11}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    :insert_clip
    move-object/from16 v15, p0
    invoke-virtual {v15}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v12
    const-string v13, "content://com.nemotron.keyboard.provider.RichcontentProvider/clipboard"
    invoke-static {v13}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
    move-result-object v13
    invoke-virtual {v12, v13, v3}, Landroid/content/ContentResolver;->insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;
    move-result-object v14
    if-eqz v14, :insert_rejected
    const-string v1, "NemotronClipboardBridge"
    const-string v2, "clipboard history provider accepted clip"
    invoke-static {v1, v2}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    goto :insert_logged
    :insert_rejected
    const-string v1, "NemotronClipboardBridge"
    const-string v2, "clipboard history insert scheduled"
    invoke-static {v1, v2}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :insert_logged
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_all
    :done
    return-void
    :catch_all
    move-exception v0
    const-string v1, "NemotronClipboardBridge"
    const-string v2, "failed to mirror primary clip"
    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method
