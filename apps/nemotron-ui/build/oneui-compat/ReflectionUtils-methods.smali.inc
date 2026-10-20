.method public static final varargs invokeReflection(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 1
    invoke-static {p0, p1, p2}, Lcom/nemotron/oneui/compat/JavaReflection;->invoke(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method

.method public static final varargs invokeConstructor(Lcom/nemotron/oneui/shaded/kotlin/reflect/KClass;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 1
    invoke-static {p0}, Lcom/nemotron/oneui/shaded/kotlin/jvm/JvmClassMappingKt;->getJavaClass(Lcom/nemotron/oneui/shaded/kotlin/reflect/KClass;)Ljava/lang/Class;
    move-result-object v0
    invoke-static {v0, p1}, Lcom/nemotron/oneui/compat/JavaReflection;->newInstance(Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method

.method public static final varargs invokeStaticMethod(Lcom/nemotron/oneui/shaded/kotlin/reflect/KClass;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 2
    invoke-static {p0}, Lcom/nemotron/oneui/shaded/kotlin/jvm/JvmClassMappingKt;->getJavaClass(Lcom/nemotron/oneui/shaded/kotlin/reflect/KClass;)Ljava/lang/Class;
    move-result-object v0
    const/4 v1, 0x0
    invoke-static {v0, v1, p1, p2}, Lcom/nemotron/oneui/compat/JavaReflection;->invokeOnClass(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method
