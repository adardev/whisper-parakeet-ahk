.class public final Lorg/lsposed/hiddenapibypass/HiddenApiBypass;
.super Ljava/lang/Object;
.source "HiddenApiBypass.java"


# static fields
.field static final synthetic $assertionsDisabled:Z = false

.field private static final TAG:Ljava/lang/String; = "HiddenApiBypass"

.field private static final artFieldBias:J

.field private static final artFieldSize:J

.field private static final artMethodBias:J

.field private static final artMethodSize:J

.field private static final artOffset:J

.field private static final classOffset:J

.field private static final fieldClassLoaders:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/ClassLoader;",
            ">;"
        }
    .end annotation
.end field

.field private static volatile fieldDeclaringClassOffset:J

.field private static volatile fieldTypeOffset:J

.field private static final iFieldOffset:J

.field private static final instanceFieldHandleSupported:Z

.field private static final methodOffset:J

.field private static final methodsOffset:J

.field private static final sFieldOffset:J

.field private static final staticFieldHandleSupported:Z

.field private static final unsafe:Lsun/misc/Unsafe;


# direct methods
.method static constructor <clinit>()V
    .locals 13

    .line 47
    nop

    .line 63
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    sput-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldClassLoaders:Ljava/util/Map;

    .line 64
    const-wide/16 v0, -0x1

    sput-wide v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldDeclaringClassOffset:J

    .line 65
    sput-wide v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldTypeOffset:J

    .line 70
    :try_start_0
    const-class v0, Lsun/misc/Unsafe;

    const-string v1, "getUnsafe"

    const/4 v2, 0x0

    new-array v3, v2, [Ljava/lang/Class;

    invoke-virtual {v0, v1, v3}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v0

    new-array v1, v2, [Ljava/lang/Object;

    const/4 v3, 0x0

    invoke-virtual {v0, v3, v1}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lsun/misc/Unsafe;

    sput-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    .line 71
    nop

    .line 72
    invoke-static {}, Lorg/lsposed/hiddenapibypass/Helper;->getCachedOffsetData()[J

    move-result-object v0

    .line 73
    .local v0, "data":[J
    if-nez v0, :cond_0

    .line 74
    const-class v1, Lorg/lsposed/hiddenapibypass/Helper$Executable;

    const-class v3, Lorg/lsposed/hiddenapibypass/Helper$MethodHandle;

    const-class v4, Lorg/lsposed/hiddenapibypass/Helper$Class;

    invoke-static {v1, v3, v4}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->readOffsetData(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;)[J

    move-result-object v1

    move-object v0, v1

    .line 75
    invoke-static {v0}, Lorg/lsposed/hiddenapibypass/Helper;->setCachedOffsetData([J)V

    .line 79
    :cond_0
    aget-wide v3, v0, v2

    sput-wide v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodOffset:J

    .line 80
    const/4 v1, 0x1

    aget-wide v3, v0, v1

    sput-wide v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->classOffset:J

    .line 81
    const/4 v3, 0x2

    aget-wide v4, v0, v3

    sput-wide v4, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    .line 82
    const/4 v4, 0x3

    aget-wide v5, v0, v4

    sput-wide v5, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodsOffset:J

    .line 83
    const/4 v5, 0x4

    aget-wide v5, v0, v5

    sput-wide v5, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->iFieldOffset:J

    .line 84
    const/4 v5, 0x5

    aget-wide v5, v0, v5

    sput-wide v5, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->sFieldOffset:J

    .line 85
    sget-wide v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodsOffset:J

    sget-wide v11, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->iFieldOffset:J

    invoke-static/range {v7 .. v12}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->readOffsetDataRT(JJJ)[J

    move-result-object v5

    .line 86
    .local v5, "rtData":[J
    aget-wide v6, v5, v2

    sput-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodSize:J

    .line 87
    aget-wide v6, v5, v1

    sput-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodBias:J

    .line 88
    aget-wide v6, v5, v3

    sput-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artFieldSize:J

    .line 89
    aget-wide v3, v5, v4

    sput-wide v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artFieldBias:J

    .line 90
    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v4, 0x25

    if-ge v3, v4, :cond_1

    const-string v3, "i"

    const-string v6, "j"

    invoke-static {v3, v6}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->isFieldHandleSupported(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_1

    move v3, v1

    goto :goto_0

    :cond_1
    move v3, v2

    :goto_0
    sput-boolean v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->instanceFieldHandleSupported:Z

    .line 91
    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    if-ge v3, v4, :cond_2

    const-string v3, "s"

    const-string v4, "t"

    invoke-static {v3, v4}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->isFieldHandleSupported(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_2

    move v2, v1

    :cond_2
    sput-boolean v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->staticFieldHandleSupported:Z
    :try_end_0
    .catch Ljava/lang/ReflectiveOperationException; {:try_start_0 .. :try_end_0} :catch_0

    .line 95
    .end local v0    # "data":[J
    .end local v5    # "rtData":[J
    nop

    .line 96
    return-void

    .line 92
    :catch_0
    move-exception v0

    .line 93
    .local v0, "e":Ljava/lang/ReflectiveOperationException;
    const-string v1, "HiddenApiBypass"

    const-string v2, "Initialize error"

    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 94
    new-instance v1, Ljava/lang/ExceptionInInitializerError;

    invoke-direct {v1, v0}, Ljava/lang/ExceptionInInitializerError;-><init>(Ljava/lang/Throwable;)V

    throw v1
.end method

.method public constructor <init>()V
    .locals 0

    .line 48
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static addFieldClassLoaderPath(Ljava/util/ArrayList;Ljava/lang/String;Ljava/lang/String;)V
    .locals 2
    .param p1, "bootClassPath"    # Ljava/lang/String;
    .param p2, "path"    # Ljava/lang/String;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/ArrayList<",
            "Ljava/lang/String;",
            ">;",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            ")V"
        }
    .end annotation

    .line 459
    .local p0, "paths":Ljava/util/ArrayList;, "Ljava/util/ArrayList<Ljava/lang/String;>;"
    invoke-virtual {p2}, Ljava/lang/String;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_3

    invoke-virtual {p0, p2}, Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    goto :goto_0

    .line 460
    :cond_0
    invoke-virtual {p1, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_1

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, ":"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    .line 461
    invoke-virtual {p1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_1

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    .line 462
    invoke-virtual {p1, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_1

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    .line 463
    invoke-virtual {p1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v0

    if-eqz v0, :cond_2

    .line 464
    :cond_1
    invoke-virtual {p0, p2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 466
    :cond_2
    return-void

    .line 459
    :cond_3
    :goto_0
    return-void
.end method

.method public static varargs addHiddenApiExemptions([Ljava/lang/String;)Z
    .locals 2
    .param p0, "signaturePrefixes"    # [Ljava/lang/String;

    .line 509
    sget-object v0, Lorg/lsposed/hiddenapibypass/Helper;->signaturePrefixes:Ljava/util/Set;

    invoke-static {p0}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/Set;->addAll(Ljava/util/Collection;)Z

    .line 510
    sget-object v0, Lorg/lsposed/hiddenapibypass/Helper;->signaturePrefixes:Ljava/util/Set;

    invoke-interface {v0}, Ljava/util/Set;->size()I

    move-result v0

    new-array v0, v0, [Ljava/lang/String;

    .line 511
    .local v0, "strings":[Ljava/lang/String;
    sget-object v1, Lorg/lsposed/hiddenapibypass/Helper;->signaturePrefixes:Ljava/util/Set;

    invoke-interface {v1, v0}, Ljava/util/Set;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;

    .line 512
    invoke-static {v0}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->setHiddenApiExemptions([Ljava/lang/String;)Z

    move-result v1

    return v1
.end method

.method public static clearHiddenApiExemptions()Z
    .locals 1

    .line 523
    sget-object v0, Lorg/lsposed/hiddenapibypass/Helper;->signaturePrefixes:Ljava/util/Set;

    invoke-interface {v0}, Ljava/util/Set;->clear()V

    .line 524
    const/4 v0, 0x0

    new-array v0, v0, [Ljava/lang/String;

    invoke-static {v0}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->setHiddenApiExemptions([Ljava/lang/String;)Z

    move-result v0

    return v0
.end method

.method private static ensureFieldOffsets()V
    .locals 7
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/ReflectiveOperationException;
        }
    .end annotation

    .line 184
    sget-wide v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldDeclaringClassOffset:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-ltz v0, :cond_0

    return-void

    .line 185
    :cond_0
    const-class v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;

    monitor-enter v0

    .line 186
    :try_start_0
    sget-wide v4, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldDeclaringClassOffset:J

    cmp-long v1, v4, v2

    if-ltz v1, :cond_1

    monitor-exit v0

    return-void

    .line 187
    :cond_1
    new-instance v1, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;

    invoke-direct {v1}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;-><init>()V

    .line 188
    .local v1, "bootClassloader":Ljava/lang/ClassLoader;
    const-class v2, Ljava/lang/reflect/Field;

    invoke-virtual {v2}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/ClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v2

    .line 189
    .local v2, "fieldClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    sget-object v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v4, "declaringClass"

    invoke-virtual {v2, v4}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v4

    invoke-virtual {v3, v4}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v3

    .line 190
    .local v3, "declaringClassOffset":J
    sget-object v5, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v6, "type"

    invoke-virtual {v2, v6}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v6

    invoke-virtual {v5, v6}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v5

    .line 191
    .local v5, "typeOffset":J
    sput-wide v5, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldTypeOffset:J

    .line 192
    sput-wide v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldDeclaringClassOffset:J

    .line 193
    .end local v1    # "bootClassloader":Ljava/lang/ClassLoader;
    .end local v2    # "fieldClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    .end local v3    # "declaringClassOffset":J
    .end local v5    # "typeOffset":J
    monitor-exit v0

    .line 194
    return-void

    .line 193
    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1
.end method

.method public static varargs getDeclaredConstructor(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/reflect/Constructor;
    .locals 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;[",
            "Ljava/lang/Class<",
            "*>;)",
            "Ljava/lang/reflect/Constructor<",
            "*>;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/NoSuchMethodException;
        }
    .end annotation

    .line 358
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    .local p1, "parameterTypes":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    invoke-static {p0}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getDeclaredMethods(Ljava/lang/Class;)Ljava/util/List;

    move-result-object v0

    .line 360
    .local v0, "methods":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Executable;>;"
    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_4

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/reflect/Executable;

    .line 361
    .local v2, "method":Ljava/lang/reflect/Executable;
    instance-of v3, v2, Ljava/lang/reflect/Constructor;

    if-nez v3, :cond_0

    goto :goto_0

    .line 362
    :cond_0
    invoke-virtual {v2}, Ljava/lang/reflect/Executable;->getParameterTypes()[Ljava/lang/Class;

    move-result-object v3

    .line 363
    .local v3, "expectedTypes":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    array-length v4, v3

    array-length v5, p1

    if-eq v4, v5, :cond_1

    goto :goto_0

    .line 364
    :cond_1
    const/4 v4, 0x0

    .local v4, "i":I
    :goto_1
    array-length v5, p1

    if-ge v4, v5, :cond_3

    .line 365
    aget-object v5, p1, v4

    aget-object v6, v3, v4

    if-eq v5, v6, :cond_2

    goto :goto_0

    .line 364
    :cond_2
    add-int/lit8 v4, v4, 0x1

    goto :goto_1

    .line 367
    .end local v4    # "i":I
    :cond_3
    move-object v1, v2

    check-cast v1, Ljava/lang/reflect/Constructor;

    return-object v1

    .line 369
    .end local v2    # "method":Ljava/lang/reflect/Executable;
    .end local v3    # "expectedTypes":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    :cond_4
    new-instance v1, Ljava/lang/NoSuchMethodException;

    const-string v2, "Cannot find matching constructor"

    invoke-direct {v1, v2}, Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V

    throw v1
.end method

.method public static varargs getDeclaredMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;
    .locals 7
    .param p1, "methodName"    # Ljava/lang/String;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;",
            "Ljava/lang/String;",
            "[",
            "Ljava/lang/Class<",
            "*>;)",
            "Ljava/lang/reflect/Method;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/NoSuchMethodException;
        }
    .end annotation

    .line 332
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    .local p2, "parameterTypes":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    invoke-static {p0}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getDeclaredMethods(Ljava/lang/Class;)Ljava/util/List;

    move-result-object v0

    .line 334
    .local v0, "methods":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Executable;>;"
    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_5

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/reflect/Executable;

    .line 335
    .local v2, "method":Ljava/lang/reflect/Executable;
    invoke-virtual {v2}, Ljava/lang/reflect/Executable;->getName()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v3, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-nez v3, :cond_0

    goto :goto_0

    .line 336
    :cond_0
    instance-of v3, v2, Ljava/lang/reflect/Method;

    if-nez v3, :cond_1

    goto :goto_0

    .line 337
    :cond_1
    invoke-virtual {v2}, Ljava/lang/reflect/Executable;->getParameterTypes()[Ljava/lang/Class;

    move-result-object v3

    .line 338
    .local v3, "expectedTypes":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    array-length v4, v3

    array-length v5, p2

    if-eq v4, v5, :cond_2

    goto :goto_0

    .line 339
    :cond_2
    const/4 v4, 0x0

    .local v4, "i":I
    :goto_1
    array-length v5, p2

    if-ge v4, v5, :cond_4

    .line 340
    aget-object v5, p2, v4

    aget-object v6, v3, v4

    if-eq v5, v6, :cond_3

    goto :goto_0

    .line 339
    :cond_3
    add-int/lit8 v4, v4, 0x1

    goto :goto_1

    .line 342
    .end local v4    # "i":I
    :cond_4
    move-object v1, v2

    check-cast v1, Ljava/lang/reflect/Method;

    return-object v1

    .line 344
    .end local v2    # "method":Ljava/lang/reflect/Executable;
    .end local v3    # "expectedTypes":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    :cond_5
    new-instance v1, Ljava/lang/NoSuchMethodException;

    const-string v2, "Cannot find matching method"

    invoke-direct {v1, v2}, Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V

    throw v1
.end method

.method public static getDeclaredMethods(Ljava/lang/Class;)Ljava/util/List;
    .locals 11
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;)",
            "Ljava/util/List<",
            "Ljava/lang/reflect/Executable;",
            ">;"
        }
    .end annotation

    .line 294
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    invoke-virtual {p0}, Ljava/lang/Class;->isPrimitive()Z

    move-result v0

    if-nez v0, :cond_3

    invoke-virtual {p0}, Ljava/lang/Class;->isArray()Z

    move-result v0

    if-eqz v0, :cond_0

    goto :goto_1

    .line 297
    :cond_0
    :try_start_0
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    const-string v1, "a"

    const/4 v2, 0x0

    new-array v2, v2, [Ljava/lang/Class;

    invoke-virtual {v0, v1, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v0

    .line 298
    .local v0, "mA":Ljava/lang/reflect/Method;
    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Ljava/lang/reflect/Method;->setAccessible(Z)V

    .line 299
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v1

    invoke-virtual {v1, v0}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflect(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;

    move-result-object v1
    :try_end_0
    .catch Ljava/lang/NoSuchMethodException; {:try_start_0 .. :try_end_0} :catch_0
    .catch Ljava/lang/IllegalAccessException; {:try_start_0 .. :try_end_0} :catch_0

    move-object v3, v1

    .line 302
    .end local v0    # "mA":Ljava/lang/reflect/Method;
    .local v3, "mh":Ljava/lang/invoke/MethodHandle;
    nop

    .line 303
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v1, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodsOffset:J

    invoke-virtual {v0, p0, v1, v2}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v0

    .line 304
    .local v0, "methods":J
    const-wide/16 v4, 0x0

    cmp-long v2, v0, v4

    if-nez v2, :cond_1

    sget-object v2, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v2

    .line 305
    :cond_1
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v2, v0, v1}, Lsun/misc/Unsafe;->getInt(J)I

    move-result v8

    .line 307
    .local v8, "numMethods":I
    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2, v8}, Ljava/util/ArrayList;-><init>(I)V

    move-object v9, v2

    .line 308
    .local v9, "list":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Executable;>;"
    const/4 v2, 0x0

    move v10, v2

    .local v10, "i":I
    :goto_0
    if-ge v10, v8, :cond_2

    .line 309
    int-to-long v4, v10

    sget-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodSize:J

    mul-long/2addr v4, v6

    add-long/2addr v4, v0

    sget-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodBias:J

    add-long/2addr v6, v4

    .line 310
    .local v6, "method":J
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v4, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    invoke-virtual/range {v2 .. v7}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 311
    const-class v2, Ljava/lang/reflect/Executable;

    invoke-static {v2, v3}, Ljava/lang/invoke/MethodHandles;->reflectAs(Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/reflect/Member;

    move-result-object v2

    check-cast v2, Ljava/lang/reflect/Executable;

    .line 315
    .local v2, "member":Ljava/lang/reflect/Executable;
    invoke-interface {v9, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 308
    .end local v2    # "member":Ljava/lang/reflect/Executable;
    .end local v6    # "method":J
    add-int/lit8 v10, v10, 0x1

    goto :goto_0

    .line 317
    .end local v10    # "i":I
    :cond_2
    return-object v9

    .line 300
    .end local v0    # "methods":J
    .end local v3    # "mh":Ljava/lang/invoke/MethodHandle;
    .end local v8    # "numMethods":I
    .end local v9    # "list":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Executable;>;"
    :catch_0
    move-exception v0

    .line 301
    .local v0, "e":Ljava/lang/ReflectiveOperationException;
    sget-object v1, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v1

    .line 294
    .end local v0    # "e":Ljava/lang/ReflectiveOperationException;
    :cond_3
    :goto_1
    sget-object v0, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v0
.end method

.method private static getFieldClassLoader(Ljava/lang/String;)Ljava/lang/ClassLoader;
    .locals 4
    .param p0, "path"    # Ljava/lang/String;

    .line 173
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldClassLoaders:Ljava/util/Map;

    monitor-enter v0

    .line 174
    :try_start_0
    sget-object v1, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldClassLoaders:Ljava/util/Map;

    invoke-interface {v1, p0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/ClassLoader;

    .line 175
    .local v1, "classLoader":Ljava/lang/ClassLoader;
    if-nez v1, :cond_0

    .line 176
    new-instance v2, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;

    const-class v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;

    invoke-virtual {v3}, Ljava/lang/Class;->getClassLoader()Ljava/lang/ClassLoader;

    move-result-object v3

    invoke-direct {v2, p0, v3}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;-><init>(Ljava/lang/String;Ljava/lang/ClassLoader;)V

    move-object v1, v2

    .line 177
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldClassLoaders:Ljava/util/Map;

    invoke-interface {v2, p0, v1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 179
    :cond_0
    monitor-exit v0

    return-object v1

    .line 180
    .end local v1    # "classLoader":Ljava/lang/ClassLoader;
    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1
.end method

.method private static getFieldClassLoaderPaths()Ljava/util/List;
    .locals 6
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation

    .line 449
    invoke-static {}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;->getBootClassPath()Ljava/lang/String;

    move-result-object v0

    .line 450
    .local v0, "bootClassPath":Ljava/lang/String;
    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    .line 451
    .local v1, "paths":Ljava/util/ArrayList;, "Ljava/util/ArrayList<Ljava/lang/String;>;"
    const-string v2, "/system/framework/framework.jar"

    invoke-static {v1, v0, v2}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->addFieldClassLoaderPath(Ljava/util/ArrayList;Ljava/lang/String;Ljava/lang/String;)V

    .line 452
    const-string v2, ":"

    invoke-virtual {v0, v2}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v2

    array-length v3, v2

    const/4 v4, 0x0

    :goto_0
    if-ge v4, v3, :cond_0

    aget-object v5, v2, v4

    .line 453
    .local v5, "path":Ljava/lang/String;
    invoke-static {v1, v0, v5}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->addFieldClassLoaderPath(Ljava/util/ArrayList;Ljava/lang/String;Ljava/lang/String;)V

    .line 452
    .end local v5    # "path":Ljava/lang/String;
    add-int/lit8 v4, v4, 0x1

    goto :goto_0

    .line 455
    :cond_0
    return-object v1
.end method

.method private static getFieldsFromClassLoader(Ljava/lang/Class;Z)Ljava/util/List;
    .locals 9
    .param p1, "wantStatic"    # Z
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;Z)",
            "Ljava/util/List<",
            "Ljava/lang/reflect/Field;",
            ">;"
        }
    .end annotation

    .line 416
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    :try_start_0
    invoke-static {}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->ensureFieldOffsets()V

    .line 417
    invoke-static {p0}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->loadCloneClass(Ljava/lang/Class;)Ljava/lang/Class;

    move-result-object v0

    .line 418
    .local v0, "clonedClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    invoke-virtual {v0}, Ljava/lang/Class;->getDeclaredFields()[Ljava/lang/reflect/Field;

    move-result-object v1

    .line 419
    .local v1, "fields":[Ljava/lang/reflect/Field;
    new-instance v2, Ljava/util/ArrayList;

    array-length v3, v1

    invoke-direct {v2, v3}, Ljava/util/ArrayList;-><init>(I)V

    .line 420
    .local v2, "list":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Field;>;"
    array-length v3, v1

    const/4 v4, 0x0

    :goto_0
    if-ge v4, v3, :cond_2

    aget-object v5, v1, v4

    .line 421
    .local v5, "field":Ljava/lang/reflect/Field;
    sget-object v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldDeclaringClassOffset:J

    invoke-virtual {v6, v5, v7, v8, p0}, Lsun/misc/Unsafe;->putObject(Ljava/lang/Object;JLjava/lang/Object;)V

    .line 422
    sget-object v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldTypeOffset:J

    invoke-virtual {v6, v5, v7, v8}, Lsun/misc/Unsafe;->getObject(Ljava/lang/Object;J)Ljava/lang/Object;

    move-result-object v6

    if-ne v6, v0, :cond_0

    .line 423
    sget-object v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->fieldTypeOffset:J

    invoke-virtual {v6, v5, v7, v8, p0}, Lsun/misc/Unsafe;->putObject(Ljava/lang/Object;JLjava/lang/Object;)V

    .line 425
    :cond_0
    invoke-virtual {v5}, Ljava/lang/reflect/Field;->getModifiers()I

    move-result v6

    invoke-static {v6}, Ljava/lang/reflect/Modifier;->isStatic(I)Z

    move-result v6

    if-ne v6, p1, :cond_1

    .line 426
    invoke-interface {v2, v5}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    :try_end_0
    .catch Ljava/lang/ReflectiveOperationException; {:try_start_0 .. :try_end_0} :catch_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0
    .catch Ljava/lang/LinkageError; {:try_start_0 .. :try_end_0} :catch_0

    .line 420
    .end local v5    # "field":Ljava/lang/reflect/Field;
    :cond_1
    add-int/lit8 v4, v4, 0x1

    goto :goto_0

    .line 429
    :cond_2
    return-object v2

    .line 430
    .end local v0    # "clonedClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    .end local v1    # "fields":[Ljava/lang/reflect/Field;
    .end local v2    # "list":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Field;>;"
    :catch_0
    move-exception v0

    .line 432
    .local v0, "e":Ljava/lang/Throwable;
    sget-object v1, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v1
.end method

.method private static getFieldsFromMethodHandle(Ljava/lang/Class;Z)Ljava/util/List;
    .locals 11
    .param p1, "wantStatic"    # Z
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;Z)",
            "Ljava/util/List<",
            "Ljava/lang/reflect/Field;",
            ">;"
        }
    .end annotation

    .line 390
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    :try_start_0
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    if-eqz p1, :cond_0

    const-string v1, "s"

    goto :goto_0

    :cond_0
    const-string v1, "i"

    :goto_0
    invoke-virtual {v0, v1}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v0

    .line 391
    .local v0, "stub":Ljava/lang/reflect/Field;
    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 392
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v1

    invoke-virtual {v1, v0}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflectGetter(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;

    move-result-object v1
    :try_end_0
    .catch Ljava/lang/IllegalAccessException; {:try_start_0 .. :try_end_0} :catch_0
    .catch Ljava/lang/NoSuchFieldException; {:try_start_0 .. :try_end_0} :catch_0

    move-object v3, v1

    .line 395
    .end local v0    # "stub":Ljava/lang/reflect/Field;
    .local v3, "mh":Ljava/lang/invoke/MethodHandle;
    nop

    .line 396
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    if-eqz p1, :cond_1

    sget-wide v1, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->sFieldOffset:J

    goto :goto_1

    :cond_1
    sget-wide v1, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->iFieldOffset:J

    :goto_1
    invoke-virtual {v0, p0, v1, v2}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v0

    .line 397
    .local v0, "fields":J
    const-wide/16 v4, 0x0

    cmp-long v2, v0, v4

    if-nez v2, :cond_2

    sget-object v2, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v2

    .line 398
    :cond_2
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v2, v0, v1}, Lsun/misc/Unsafe;->getInt(J)I

    move-result v8

    .line 400
    .local v8, "numFields":I
    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2, v8}, Ljava/util/ArrayList;-><init>(I)V

    move-object v9, v2

    .line 401
    .local v9, "list":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Field;>;"
    const/4 v2, 0x0

    move v10, v2

    .local v10, "i":I
    :goto_2
    if-ge v10, v8, :cond_4

    .line 402
    int-to-long v4, v10

    sget-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artFieldSize:J

    mul-long/2addr v4, v6

    add-long/2addr v4, v0

    sget-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artFieldBias:J

    add-long/2addr v6, v4

    .line 403
    .local v6, "field":J
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v4, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    invoke-virtual/range {v2 .. v7}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 404
    const-class v2, Ljava/lang/reflect/Field;

    invoke-static {v2, v3}, Ljava/lang/invoke/MethodHandles;->reflectAs(Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/reflect/Member;

    move-result-object v2

    check-cast v2, Ljava/lang/reflect/Field;

    .line 407
    .local v2, "member":Ljava/lang/reflect/Field;
    invoke-virtual {v2}, Ljava/lang/reflect/Field;->getModifiers()I

    move-result v4

    invoke-static {v4}, Ljava/lang/reflect/Modifier;->isStatic(I)Z

    move-result v4

    if-ne v4, p1, :cond_3

    .line 408
    invoke-interface {v9, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 401
    .end local v2    # "member":Ljava/lang/reflect/Field;
    .end local v6    # "field":J
    :cond_3
    add-int/lit8 v10, v10, 0x1

    goto :goto_2

    .line 410
    .end local v10    # "i":I
    :cond_4
    return-object v9

    .line 393
    .end local v0    # "fields":J
    .end local v3    # "mh":Ljava/lang/invoke/MethodHandle;
    .end local v8    # "numFields":I
    .end local v9    # "list":Ljava/util/List;, "Ljava/util/List<Ljava/lang/reflect/Field;>;"
    :catch_0
    move-exception v0

    .line 394
    .local v0, "e":Ljava/lang/ReflectiveOperationException;
    sget-object v1, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v1
.end method

.method public static getInstanceFields(Ljava/lang/Class;)Ljava/util/List;
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;)",
            "Ljava/util/List<",
            "Ljava/lang/reflect/Field;",
            ">;"
        }
    .end annotation

    .line 381
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    invoke-virtual {p0}, Ljava/lang/Class;->isPrimitive()Z

    move-result v0

    if-nez v0, :cond_2

    invoke-virtual {p0}, Ljava/lang/Class;->isArray()Z

    move-result v0

    if-eqz v0, :cond_0

    goto :goto_0

    .line 382
    :cond_0
    sget-boolean v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->instanceFieldHandleSupported:Z

    const/4 v1, 0x0

    if-nez v0, :cond_1

    invoke-static {p0, v1}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getFieldsFromClassLoader(Ljava/lang/Class;Z)Ljava/util/List;

    move-result-object v0

    return-object v0

    .line 383
    :cond_1
    invoke-static {p0, v1}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getFieldsFromMethodHandle(Ljava/lang/Class;Z)Ljava/util/List;

    move-result-object v0

    return-object v0

    .line 381
    :cond_2
    :goto_0
    sget-object v0, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v0
.end method

.method public static getStaticFields(Ljava/lang/Class;)Ljava/util/List;
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;)",
            "Ljava/util/List<",
            "Ljava/lang/reflect/Field;",
            ">;"
        }
    .end annotation

    .line 476
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    invoke-virtual {p0}, Ljava/lang/Class;->isPrimitive()Z

    move-result v0

    if-nez v0, :cond_2

    invoke-virtual {p0}, Ljava/lang/Class;->isArray()Z

    move-result v0

    if-eqz v0, :cond_0

    goto :goto_0

    .line 477
    :cond_0
    sget-boolean v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->staticFieldHandleSupported:Z

    const/4 v1, 0x1

    if-nez v0, :cond_1

    invoke-static {p0, v1}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getFieldsFromClassLoader(Ljava/lang/Class;Z)Ljava/util/List;

    move-result-object v0

    return-object v0

    .line 478
    :cond_1
    invoke-static {p0, v1}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getFieldsFromMethodHandle(Ljava/lang/Class;Z)Ljava/util/List;

    move-result-object v0

    return-object v0

    .line 476
    :cond_2
    :goto_0
    sget-object v0, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    return-object v0
.end method

.method public static varargs invoke(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 11
    .param p1, "thiz"    # Ljava/lang/Object;
    .param p2, "methodName"    # Ljava/lang/String;
    .param p3, "args"    # [Ljava/lang/Object;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;",
            "Ljava/lang/Object;",
            "Ljava/lang/String;",
            "[",
            "Ljava/lang/Object;",
            ")",
            "Ljava/lang/Object;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/NoSuchMethodException;,
            Ljava/lang/reflect/InvocationTargetException;,
            Ljava/lang/IllegalAccessException;
        }
    .end annotation

    .line 263
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    if-eqz p1, :cond_1

    invoke-virtual {p0, p1}, Ljava/lang/Class;->isInstance(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    goto :goto_0

    .line 264
    :cond_0
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v1, "this object is not an instance of the given class"

    invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    .line 266
    :cond_1
    :goto_0
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$InvokeStub;

    const/4 v1, 0x1

    new-array v2, v1, [Ljava/lang/Class;

    const/4 v3, 0x0

    const-class v4, [Ljava/lang/Object;

    aput-object v4, v2, v3

    const-string v3, "invoke"

    invoke-virtual {v0, v3, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v5

    .line 267
    .local v5, "stub":Ljava/lang/reflect/Method;
    invoke-virtual {v5, v1}, Ljava/lang/reflect/Method;->setAccessible(Z)V

    .line 268
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v1, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodsOffset:J

    invoke-virtual {v0, p0, v1, v2}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v0

    .line 269
    .local v0, "methods":J
    const-wide/16 v2, 0x0

    cmp-long v2, v0, v2

    const-string v3, "Cannot find matching method"

    if-eqz v2, :cond_4

    .line 270
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v2, v0, v1}, Lsun/misc/Unsafe;->getInt(J)I

    move-result v2

    .line 272
    .local v2, "numMethods":I
    const/4 v4, 0x0

    move v10, v4

    .local v10, "i":I
    :goto_1
    if-ge v10, v2, :cond_3

    .line 273
    int-to-long v6, v10

    sget-wide v8, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodSize:J

    mul-long/2addr v6, v8

    add-long/2addr v6, v0

    sget-wide v8, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodBias:J

    add-long/2addr v8, v6

    .line 274
    .local v8, "method":J
    sget-object v4, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v6, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodOffset:J

    invoke-virtual/range {v4 .. v9}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 277
    invoke-virtual {v5}, Ljava/lang/reflect/Method;->getName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {p2, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_2

    .line 278
    invoke-virtual {v5}, Ljava/lang/reflect/Method;->getParameterTypes()[Ljava/lang/Class;

    move-result-object v4

    .line 279
    .local v4, "params":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    invoke-static {v4, p3}, Lorg/lsposed/hiddenapibypass/Helper;->checkArgsForInvokeMethod([Ljava/lang/Class;[Ljava/lang/Object;)Z

    move-result v6

    if-eqz v6, :cond_2

    .line 280
    invoke-virtual {v5, p1, p3}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    return-object v3

    .line 272
    .end local v4    # "params":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    .end local v8    # "method":J
    :cond_2
    add-int/lit8 v10, v10, 0x1

    goto :goto_1

    .line 283
    .end local v10    # "i":I
    :cond_3
    new-instance v4, Ljava/lang/NoSuchMethodException;

    invoke-direct {v4, v3}, Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V

    throw v4

    .line 269
    .end local v2    # "numMethods":I
    :cond_4
    new-instance v2, Ljava/lang/NoSuchMethodException;

    invoke-direct {v2, v3}, Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V

    throw v2
.end method

.method private static isFieldHandleSupported(Ljava/lang/String;Ljava/lang/String;)Z
    .locals 18
    .param p0, "sourceName"    # Ljava/lang/String;
    .param p1, "targetName"    # Ljava/lang/String;

    .line 198
    move-object/from16 v1, p1

    const/4 v2, 0x0

    :try_start_0
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;
    :try_end_0
    .catch Ljava/lang/ReflectiveOperationException; {:try_start_0 .. :try_end_0} :catch_1
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_1
    .catch Ljava/lang/LinkageError; {:try_start_0 .. :try_end_0} :catch_1

    move-object/from16 v3, p0

    :try_start_1
    invoke-virtual {v0, v3}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v0

    move-object v4, v0

    .line 199
    .local v4, "source":Ljava/lang/reflect/Field;
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    invoke-virtual {v0, v1}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v0

    move-object v5, v0

    .line 200
    .local v5, "target":Ljava/lang/reflect/Field;
    const/4 v0, 0x1

    invoke-virtual {v4, v0}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 201
    invoke-virtual {v5, v0}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 202
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v6

    invoke-virtual {v6, v4}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflectGetter(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;

    move-result-object v6

    move-object v8, v6

    .line 203
    .local v8, "handle":Ljava/lang/invoke/MethodHandle;
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v6

    invoke-virtual {v6, v5}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflectGetter(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;

    move-result-object v6

    .line 204
    .local v6, "targetHandle":Ljava/lang/invoke/MethodHandle;
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    invoke-virtual {v7, v8, v9, v10}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v11

    move-wide v13, v11

    .line 205
    .local v13, "sourceArtField":J
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    invoke-virtual {v7, v6, v9, v10}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v11
    :try_end_1
    .catch Ljava/lang/ReflectiveOperationException; {:try_start_1 .. :try_end_1} :catch_0
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_0
    .catch Ljava/lang/LinkageError; {:try_start_1 .. :try_end_1} :catch_0

    .line 207
    .local v11, "targetArtField":J
    :try_start_2
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    invoke-virtual/range {v7 .. v12}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_2

    move-wide v15, v11

    .line 208
    .end local v11    # "targetArtField":J
    .local v15, "targetArtField":J
    :try_start_3
    const-class v7, Ljava/lang/reflect/Field;

    invoke-static {v7, v8}, Ljava/lang/invoke/MethodHandles;->reflectAs(Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/reflect/Member;

    move-result-object v7

    check-cast v7, Ljava/lang/reflect/Field;

    move-object/from16 v17, v7

    .line 209
    .local v17, "reflected":Ljava/lang/reflect/Field;
    invoke-virtual/range {v17 .. v17}, Ljava/lang/reflect/Field;->getDeclaringClass()Ljava/lang/Class;

    move-result-object v7

    const-class v9, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_1

    if-ne v7, v9, :cond_0

    .line 210
    :try_start_4
    invoke-virtual/range {v17 .. v17}, Ljava/lang/reflect/Field;->getName()Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v1, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v7
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_0

    if-eqz v7, :cond_0

    goto :goto_0

    .line 212
    .end local v17    # "reflected":Ljava/lang/reflect/Field;
    :catchall_0
    move-exception v0

    move-wide v11, v13

    goto :goto_1

    .line 210
    .restart local v17    # "reflected":Ljava/lang/reflect/Field;
    :cond_0
    move v0, v2

    .line 212
    :goto_0
    :try_start_5
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    move-wide v11, v13

    .end local v13    # "sourceArtField":J
    .local v11, "sourceArtField":J
    invoke-virtual/range {v7 .. v12}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 209
    return v0

    .line 212
    .end local v11    # "sourceArtField":J
    .end local v17    # "reflected":Ljava/lang/reflect/Field;
    .restart local v13    # "sourceArtField":J
    :catchall_1
    move-exception v0

    move-wide v11, v13

    .end local v13    # "sourceArtField":J
    .restart local v11    # "sourceArtField":J
    goto :goto_1

    .end local v15    # "targetArtField":J
    .local v11, "targetArtField":J
    .restart local v13    # "sourceArtField":J
    :catchall_2
    move-exception v0

    move-wide v15, v11

    move-wide v11, v13

    .end local v13    # "sourceArtField":J
    .local v11, "sourceArtField":J
    .restart local v15    # "targetArtField":J
    :goto_1
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artOffset:J

    invoke-virtual/range {v7 .. v12}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 213
    nop

    .end local p0    # "sourceName":Ljava/lang/String;
    .end local p1    # "targetName":Ljava/lang/String;
    throw v0
    :try_end_5
    .catch Ljava/lang/ReflectiveOperationException; {:try_start_5 .. :try_end_5} :catch_0
    .catch Ljava/lang/RuntimeException; {:try_start_5 .. :try_end_5} :catch_0
    .catch Ljava/lang/LinkageError; {:try_start_5 .. :try_end_5} :catch_0

    .line 214
    .end local v4    # "source":Ljava/lang/reflect/Field;
    .end local v5    # "target":Ljava/lang/reflect/Field;
    .end local v6    # "targetHandle":Ljava/lang/invoke/MethodHandle;
    .end local v8    # "handle":Ljava/lang/invoke/MethodHandle;
    .end local v11    # "sourceArtField":J
    .end local v15    # "targetArtField":J
    .restart local p0    # "sourceName":Ljava/lang/String;
    .restart local p1    # "targetName":Ljava/lang/String;
    :catch_0
    move-exception v0

    goto :goto_2

    :catch_1
    move-exception v0

    move-object/from16 v3, p0

    .line 216
    .local v0, "e":Ljava/lang/Throwable;
    :goto_2
    return v2
.end method

.method private static isMergedFields(J)Z
    .locals 4
    .param p0, "fieldOffset"    # J

    .line 168
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-class v1, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    invoke-virtual {v0, v1, p0, p1}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v0

    .line 169
    .local v0, "fields":J
    const-wide/16 v2, 0x0

    cmp-long v2, v0, v2

    if-eqz v2, :cond_0

    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v2, v0, v1}, Lsun/misc/Unsafe;->getInt(J)I

    move-result v2

    const/4 v3, 0x4

    if-ne v2, v3, :cond_0

    const/4 v2, 0x1

    goto :goto_0

    :cond_0
    const/4 v2, 0x0

    :goto_0
    return v2
.end method

.method private static loadCloneClass(Ljava/lang/Class;)Ljava/lang/Class;
    .locals 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;)",
            "Ljava/lang/Class<",
            "*>;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/ClassNotFoundException;
        }
    .end annotation

    .line 437
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    invoke-virtual {p0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    .line 438
    .local v0, "className":Ljava/lang/String;
    invoke-static {}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getFieldClassLoaderPaths()Ljava/util/List;

    move-result-object v1

    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_1

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    .line 440
    .local v2, "path":Ljava/lang/String;
    :try_start_0
    invoke-static {v2}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->getFieldClassLoader(Ljava/lang/String;)Ljava/lang/ClassLoader;

    move-result-object v3

    invoke-virtual {v3, v0}, Ljava/lang/ClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3
    :try_end_0
    .catch Ljava/lang/ClassNotFoundException; {:try_start_0 .. :try_end_0} :catch_0

    .line 441
    .local v3, "clonedClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    if-eq v3, p0, :cond_0

    return-object v3

    .line 443
    .end local v3    # "clonedClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    :cond_0
    goto :goto_1

    .line 442
    :catch_0
    move-exception v3

    .line 444
    .end local v2    # "path":Ljava/lang/String;
    :goto_1
    goto :goto_0

    .line 445
    :cond_1
    new-instance v1, Ljava/lang/ClassNotFoundException;

    invoke-direct {v1, v0}, Ljava/lang/ClassNotFoundException;-><init>(Ljava/lang/String;)V

    throw v1
.end method

.method public static varargs newInstance(Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
    .locals 14
    .param p1, "initargs"    # [Ljava/lang/Object;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;[",
            "Ljava/lang/Object;",
            ")",
            "Ljava/lang/Object;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/NoSuchMethodException;,
            Ljava/lang/IllegalAccessException;,
            Ljava/lang/reflect/InvocationTargetException;,
            Ljava/lang/InstantiationException;
        }
    .end annotation

    .line 229
    .local p0, "clazz":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$InvokeStub;

    const/4 v1, 0x1

    new-array v2, v1, [Ljava/lang/Class;

    const-class v3, [Ljava/lang/Object;

    const/4 v4, 0x0

    aput-object v3, v2, v4

    const-string v3, "invoke"

    invoke-virtual {v0, v3, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v6

    .line 230
    .local v6, "stub":Ljava/lang/reflect/Method;
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$InvokeStub;

    new-array v2, v1, [Ljava/lang/Class;

    const-class v3, [Ljava/lang/Object;

    aput-object v3, v2, v4

    invoke-virtual {v0, v2}, Ljava/lang/Class;->getDeclaredConstructor([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;

    move-result-object v0

    .line 231
    .local v0, "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    invoke-virtual {v0, v1}, Ljava/lang/reflect/Constructor;->setAccessible(Z)V

    .line 232
    sget-object v1, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodsOffset:J

    invoke-virtual {v1, p0, v2, v3}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v1

    .line 233
    .local v1, "methods":J
    const-wide/16 v3, 0x0

    cmp-long v3, v1, v3

    const-string v4, "Cannot find matching constructor"

    if-eqz v3, :cond_3

    .line 234
    sget-object v3, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v3, v1, v2}, Lsun/misc/Unsafe;->getInt(J)I

    move-result v3

    .line 236
    .local v3, "numMethods":I
    const/4 v5, 0x0

    move v13, v5

    .local v13, "i":I
    :goto_0
    if-ge v13, v3, :cond_2

    .line 237
    int-to-long v7, v13

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodSize:J

    mul-long/2addr v7, v9

    add-long/2addr v7, v1

    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->artMethodBias:J

    add-long/2addr v9, v7

    .line 238
    .local v9, "method":J
    sget-object v5, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodOffset:J

    invoke-virtual/range {v5 .. v10}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 241
    const-string v5, "<init>"

    invoke-virtual {v6}, Ljava/lang/reflect/Method;->getName()Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v5, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v5

    if-eqz v5, :cond_0

    .line 242
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    move-wide v11, v9

    .end local v9    # "method":J
    .local v11, "method":J
    sget-wide v9, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->methodOffset:J

    move-object v8, v0

    .end local v0    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .local v8, "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    invoke-virtual/range {v7 .. v12}, Lsun/misc/Unsafe;->putLong(Ljava/lang/Object;JJ)V

    .line 243
    move-wide v9, v11

    .end local v11    # "method":J
    .restart local v9    # "method":J
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    sget-wide v11, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->classOffset:J

    invoke-virtual {v0, v8, v11, v12, p0}, Lsun/misc/Unsafe;->putObject(Ljava/lang/Object;JLjava/lang/Object;)V

    .line 244
    invoke-virtual {v8}, Ljava/lang/reflect/Constructor;->getParameterTypes()[Ljava/lang/Class;

    move-result-object v0

    .line 245
    .local v0, "params":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    invoke-static {v0, p1}, Lorg/lsposed/hiddenapibypass/Helper;->checkArgsForInvokeMethod([Ljava/lang/Class;[Ljava/lang/Object;)Z

    move-result v5

    if-eqz v5, :cond_1

    .line 246
    invoke-virtual {v8, p1}, Ljava/lang/reflect/Constructor;->newInstance([Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v4

    return-object v4

    .line 241
    .end local v8    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .local v0, "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    :cond_0
    move-object v8, v0

    .line 236
    .end local v0    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .end local v9    # "method":J
    .restart local v8    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    :cond_1
    add-int/lit8 v13, v13, 0x1

    move-object v0, v8

    goto :goto_0

    .end local v8    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .restart local v0    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    :cond_2
    move-object v8, v0

    .line 249
    .end local v0    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .end local v13    # "i":I
    .restart local v8    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    new-instance v0, Ljava/lang/NoSuchMethodException;

    invoke-direct {v0, v4}, Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V

    throw v0

    .line 233
    .end local v3    # "numMethods":I
    .end local v8    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .restart local v0    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    :cond_3
    move-object v8, v0

    .end local v0    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    .restart local v8    # "ctor":Ljava/lang/reflect/Constructor;, "Ljava/lang/reflect/Constructor<*>;"
    new-instance v0, Ljava/lang/NoSuchMethodException;

    invoke-direct {v0, v4}, Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method private static readOffsetData(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;)[J
    .locals 17
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Class<",
            "*>;",
            "Ljava/lang/Class<",
            "*>;",
            "Ljava/lang/Class<",
            "*>;)[J"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/ReflectiveOperationException;
        }
    .end annotation

    .line 99
    .local p0, "executableClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    .local p1, "methodHandleClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    .local p2, "classClass":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
    move-object/from16 v1, p0

    move-object/from16 v2, p2

    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v3, "artMethod"

    invoke-virtual {v1, v3}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v3

    invoke-virtual {v0, v3}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v3

    .line 100
    .local v3, "methodOffset":J
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v5, "declaringClass"

    invoke-virtual {v1, v5}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v5

    invoke-virtual {v0, v5}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v5

    .line 101
    .local v5, "classOffset":J
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v7, "artFieldOrMethod"

    move-object/from16 v8, p1

    invoke-virtual {v8, v7}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v7

    invoke-virtual {v0, v7}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v9

    .line 102
    .local v9, "artOffset":J
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v7, "methods"

    invoke-virtual {v2, v7}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v7

    invoke-virtual {v0, v7}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v11

    .line 106
    .local v11, "methodsOffset":J
    :try_start_0
    sget-object v0, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v7, "fields"

    invoke-virtual {v2, v7}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v7

    invoke-virtual {v0, v7}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v13
    :try_end_0
    .catch Ljava/lang/NoSuchFieldException; {:try_start_0 .. :try_end_0} :catch_0

    .line 107
    .local v13, "iFieldOffset":J
    move-wide v15, v13

    .line 114
    .local v15, "sFieldOffset":J
    goto :goto_0

    .line 108
    .end local v13    # "iFieldOffset":J
    .end local v15    # "sFieldOffset":J
    :catch_0
    move-exception v0

    .line 109
    .local v0, "e":Ljava/lang/NoSuchFieldException;
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v13, "iFields"

    invoke-virtual {v2, v13}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v13

    invoke-virtual {v7, v13}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v13

    .line 110
    .restart local v13    # "iFieldOffset":J
    sget-object v7, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-string v15, "sFields"

    invoke-virtual {v2, v15}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v15

    invoke-virtual {v7, v15}, Lsun/misc/Unsafe;->objectFieldOffset(Ljava/lang/reflect/Field;)J

    move-result-wide v15

    .line 111
    .restart local v15    # "sFieldOffset":J
    invoke-static {v13, v14}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->isMergedFields(J)Z

    move-result v7

    if-eqz v7, :cond_0

    .line 112
    move-wide v15, v13

    .line 116
    .end local v0    # "e":Ljava/lang/NoSuchFieldException;
    :cond_0
    :goto_0
    const/4 v0, 0x6

    new-array v0, v0, [J

    const/4 v7, 0x0

    aput-wide v3, v0, v7

    const/4 v7, 0x1

    aput-wide v5, v0, v7

    const/4 v7, 0x2

    aput-wide v9, v0, v7

    const/4 v7, 0x3

    aput-wide v11, v0, v7

    const/4 v7, 0x4

    aput-wide v13, v0, v7

    const/4 v7, 0x5

    aput-wide v15, v0, v7

    return-object v0
.end method

.method private static readOffsetDataRT(JJJ)[J
    .locals 36
    .param p0, "artOffset"    # J
    .param p2, "methodsOffset"    # J
    .param p4, "iFieldOffset"    # J
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/ReflectiveOperationException;
        }
    .end annotation

    .line 127
    move-wide/from16 v0, p0

    const-class v2, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    const/4 v3, 0x0

    new-array v4, v3, [Ljava/lang/Class;

    const-string v5, "a"

    invoke-virtual {v2, v5, v4}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v2

    .line 128
    .local v2, "mA":Ljava/lang/reflect/Method;
    const-class v4, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    const-string v5, "b"

    new-array v6, v3, [Ljava/lang/Class;

    invoke-virtual {v4, v5, v6}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v4

    .line 129
    .local v4, "mB":Ljava/lang/reflect/Method;
    const/4 v5, 0x1

    invoke-virtual {v2, v5}, Ljava/lang/reflect/Method;->setAccessible(Z)V

    .line 130
    invoke-virtual {v4, v5}, Ljava/lang/reflect/Method;->setAccessible(Z)V

    .line 131
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v6

    invoke-virtual {v6, v2}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflect(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;

    move-result-object v6

    .line 132
    .local v6, "mhA":Ljava/lang/invoke/MethodHandle;
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v7

    invoke-virtual {v7, v4}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflect(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;

    move-result-object v7

    .line 133
    .local v7, "mhB":Ljava/lang/invoke/MethodHandle;
    sget-object v8, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v8, v6, v0, v1}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v8

    .line 134
    .local v8, "aAddr":J
    sget-object v10, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v10, v7, v0, v1}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v10

    .line 135
    .local v10, "bAddr":J
    sget-object v12, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-class v13, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    move-wide/from16 v14, p2

    invoke-virtual {v12, v13, v14, v15}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v12

    .line 136
    .local v12, "aMethods":J
    sub-long v16, v10, v8

    .line 141
    .local v16, "artMethodSize":J
    sub-long v18, v8, v12

    sub-long v18, v18, v16

    .line 143
    .local v18, "artMethodBias":J
    move/from16 v20, v3

    const-class v3, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    const-string v5, "i"

    invoke-virtual {v3, v5}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v3

    .line 144
    .local v3, "fI":Ljava/lang/reflect/Field;
    const-class v5, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    move-object/from16 v22, v2

    .end local v2    # "mA":Ljava/lang/reflect/Method;
    .local v22, "mA":Ljava/lang/reflect/Method;
    const-string v2, "j"

    invoke-virtual {v5, v2}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v2

    .line 145
    .local v2, "fJ":Ljava/lang/reflect/Field;
    const/4 v5, 0x1

    invoke-virtual {v3, v5}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 146
    invoke-virtual {v2, v5}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 147
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v5

    invoke-virtual {v5, v3}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflectGetter(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;

    move-result-object v5

    .line 148
    .local v5, "mhI":Ljava/lang/invoke/MethodHandle;
    move-object/from16 v23, v3

    .end local v3    # "fI":Ljava/lang/reflect/Field;
    .local v23, "fI":Ljava/lang/reflect/Field;
    invoke-static {}, Ljava/lang/invoke/MethodHandles;->lookup()Ljava/lang/invoke/MethodHandles$Lookup;

    move-result-object v3

    invoke-virtual {v3, v2}, Ljava/lang/invoke/MethodHandles$Lookup;->unreflectGetter(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;

    move-result-object v3

    .line 149
    .local v3, "mhJ":Ljava/lang/invoke/MethodHandle;
    move-object/from16 v24, v2

    .end local v2    # "fJ":Ljava/lang/reflect/Field;
    .local v24, "fJ":Ljava/lang/reflect/Field;
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v2, v5, v0, v1}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v25

    .line 150
    .local v25, "iAddr":J
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    invoke-virtual {v2, v3, v0, v1}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v27

    .line 151
    .local v27, "jAddr":J
    sget-object v2, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->unsafe:Lsun/misc/Unsafe;

    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$NeverCall;

    move-object/from16 v29, v3

    move-object v1, v4

    move-wide/from16 v3, p4

    .end local v3    # "mhJ":Ljava/lang/invoke/MethodHandle;
    .end local v4    # "mB":Ljava/lang/reflect/Method;
    .local v1, "mB":Ljava/lang/reflect/Method;
    .local v29, "mhJ":Ljava/lang/invoke/MethodHandle;
    invoke-virtual {v2, v0, v3, v4}, Lsun/misc/Unsafe;->getLong(Ljava/lang/Object;J)J

    move-result-wide v30

    .line 152
    .local v30, "iFields":J
    sub-long v32, v27, v25

    .line 157
    .local v32, "artFieldSize":J
    sub-long v34, v25, v30

    .line 159
    .local v34, "artFieldBias":J
    const/4 v0, 0x4

    new-array v0, v0, [J

    aput-wide v16, v0, v20

    const/16 v21, 0x1

    aput-wide v18, v0, v21

    const/4 v2, 0x2

    aput-wide v32, v0, v2

    const/4 v2, 0x3

    aput-wide v34, v0, v2

    return-object v0
.end method

.method public static varargs setHiddenApiExemptions([Ljava/lang/String;)Z
    .locals 6
    .param p0, "signaturePrefixes"    # [Ljava/lang/String;

    .line 491
    const-string v0, "setHiddenApiExemptions"

    const/4 v1, 0x0

    :try_start_0
    const-class v2, Ldalvik/system/VMRuntime;

    const-string v3, "getRuntime"

    new-array v4, v1, [Ljava/lang/Object;

    const/4 v5, 0x0

    invoke-static {v2, v5, v3, v4}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->invoke(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    .line 492
    .local v2, "runtime":Ljava/lang/Object;
    const-class v3, Ldalvik/system/VMRuntime;

    filled-new-array {p0}, [Ljava/lang/Object;

    move-result-object v4

    invoke-static {v3, v2, v0, v4}, Lorg/lsposed/hiddenapibypass/HiddenApiBypass;->invoke(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
    :try_end_0
    .catch Ljava/lang/ReflectiveOperationException; {:try_start_0 .. :try_end_0} :catch_0

    .line 493
    const/4 v0, 0x1

    return v0

    .line 494
    .end local v2    # "runtime":Ljava/lang/Object;
    :catch_0
    move-exception v2

    .line 495
    .local v2, "e":Ljava/lang/ReflectiveOperationException;
    const-string v3, "HiddenApiBypass"

    invoke-static {v3, v0, v2}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 496
    return v1
.end method
