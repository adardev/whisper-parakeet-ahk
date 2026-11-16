.class public final Lcom/nemotron/oneui/compat/AddAppsPreferenceClick;
.super Ljava/lang/Object;
.implements Landroidx/preference/Preference$OnPreferenceClickListener;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public onPreferenceClick(Landroidx/preference/Preference;)Z
    .locals 1
    invoke-virtual {p1}, Landroidx/preference/Preference;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {v0}, Lcom/nemotron/oneui/compat/NemotronAppPicker;->show(Landroid/content/Context;)V
    const/4 v0, 0x1
    return v0
.end method
