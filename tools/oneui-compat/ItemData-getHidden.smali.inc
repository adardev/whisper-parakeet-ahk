.method public final getHidden()Lcom/honeyspace/sdk/database/field/HiddenType;
    .locals 2
    iget-object v0, p0, Lcom/honeyspace/sdk/database/entity/ItemData;->type:Lcom/honeyspace/sdk/database/field/ItemType;
    sget-object v1, Lcom/honeyspace/sdk/database/field/ItemType;->APP:Lcom/honeyspace/sdk/database/field/ItemType;
    if-ne v0, v1, :original
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
