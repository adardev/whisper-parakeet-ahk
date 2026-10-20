.class public final Lcom/nemotron/oneui/compat/JavaReflection;
.super Ljava/lang/Object;

.method public static invoke(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 1
    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
    move-result-object v0
    invoke-static {v0, p0, p1, p2}, Lcom/nemotron/oneui/compat/JavaReflection;->invokeOnClass(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method

.method public static invokeOnClass(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 6
    invoke-virtual {p0}, Ljava/lang/Class;->getMethods()[Ljava/lang/reflect/Method;
    move-result-object v0
    const/4 v1, 0x0
    :loop
    array-length v4, v0
    if-ge v1, v4, :missing
    aget-object v2, v0, v1
    invoke-virtual {v2}, Ljava/lang/reflect/Method;->getName()Ljava/lang/String;
    move-result-object v3
    invoke-virtual {p2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v3
    if-eqz v3, :next
    invoke-virtual {v2}, Ljava/lang/reflect/Method;->getParameterTypes()[Ljava/lang/Class;
    move-result-object v3
    array-length v4, v3
    array-length v5, p3
    if-ne v4, v5, :next
    :try_start
    invoke-virtual {v2, p1, p3}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v3
    return-object v3
    :try_end
    .catch Ljava/lang/Exception; {:try_start .. :try_end} :failed
    :failed
    move-exception v3
    :next
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :missing
    const/4 v0, 0x0
    return-object v0
.end method

.method public static newInstance(Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 5
    invoke-virtual {p0}, Ljava/lang/Class;->getConstructors()[Ljava/lang/reflect/Constructor;
    move-result-object v0
    const/4 v1, 0x0
    :loop
    array-length v3, v0
    if-ge v1, v3, :missing
    aget-object v2, v0, v1
    invoke-virtual {v2}, Ljava/lang/reflect/Constructor;->getParameterTypes()[Ljava/lang/Class;
    move-result-object v3
    array-length v3, v3
    array-length v4, p1
    if-ne v3, v4, :next
    :try_start
    invoke-virtual {v2, p1}, Ljava/lang/reflect/Constructor;->newInstance([Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v2
    return-object v2
    :try_end
    .catch Ljava/lang/Exception; {:try_start .. :try_end} :failed
    :failed
    move-exception v2
    :next
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :missing
    const/4 v0, 0x0
    return-object v0
.end method
