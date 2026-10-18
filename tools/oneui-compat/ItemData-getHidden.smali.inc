.method public final getHidden()Lcom/honeyspace/sdk/database/field/HiddenType;
    .locals 2
    iget-object v1, p0, Lcom/honeyspace/sdk/database/entity/ItemData;->component:Ljava/lang/String;
    if-eqz v1, :reference
    const-string v0, "com.instagram.android/"
    invoke-virtual {v1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :hidden
    const-string v0, "com.whatsapp/"
    invoke-virtual {v1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :hidden
    :reference
    iget-object v1, p0, Lcom/honeyspace/sdk/database/entity/ItemData;->refPackageName:Ljava/lang/String;
    const-string v0, "com.instagram.android"
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :hidden
    const-string v0, "com.whatsapp"
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :original
    :hidden
    sget-object v0, Lcom/honeyspace/sdk/database/field/HiddenType;->USER:Lcom/honeyspace/sdk/database/field/HiddenType;
    return-object v0
    :original
    iget-object v0, p0, Lcom/honeyspace/sdk/database/entity/ItemData;->hidden:Lcom/honeyspace/sdk/database/field/HiddenType;
    return-object v0
.end method
