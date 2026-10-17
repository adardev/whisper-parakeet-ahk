.class public final Lcom/nemotron/oneui/compat/AddAppsPreferenceClick;
.super Ljava/lang/Object;
.implements Lcom/nemotron/oneui/shaded/androidx/preference/Preference$OnPreferenceClickListener;
.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method
.method public onPreferenceClick(Lcom/nemotron/oneui/shaded/androidx/preference/Preference;)Z
    .locals 4
    invoke-virtual {p1}, Lcom/nemotron/oneui/shaded/androidx/preference/Preference;->getContext()Landroid/content/Context;
    move-result-object v0
    new-instance v1, Landroid/content/Intent;
    invoke-direct {v1}, Landroid/content/Intent;-><init>()V
    const-string v2, "com.nemotron.voiceime2"
    const-string v3, "com.nemotron.voiceime.ui.ShortcutPickerActivity"
    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;
    invoke-virtual {v0, v1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    const/4 v0, 0x1
    return v0
.end method
