.class public Lorg/lsposed/hiddenapibypass/Helper;
.super Ljava/lang/Object;
.source "Helper.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lorg/lsposed/hiddenapibypass/Helper$InvokeStub;,
        Lorg/lsposed/hiddenapibypass/Helper$NeverCall;,
        Lorg/lsposed/hiddenapibypass/Helper$Field;,
        Lorg/lsposed/hiddenapibypass/Helper$Executable;,
        Lorg/lsposed/hiddenapibypass/Helper$AccessibleObject;,
        Lorg/lsposed/hiddenapibypass/Helper$Class;,
        Lorg/lsposed/hiddenapibypass/Helper$MethodHandle;
    }
.end annotation


# static fields
.field private static cachedOffsetData:[J

.field static final signaturePrefixes:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 25
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    sput-object v0, Lorg/lsposed/hiddenapibypass/Helper;->signaturePrefixes:Ljava/util/Set;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 24
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static checkArgsForInvokeMethod([Ljava/lang/Class;[Ljava/lang/Object;)Z
    .locals 4
    .param p1, "args"    # [Ljava/lang/Object;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "([",
            "Ljava/lang/Class<",
            "*>;[",
            "Ljava/lang/Object;",
            ")Z"
        }
    .end annotation

    .line 41
    .local p0, "params":[Ljava/lang/Class;, "[Ljava/lang/Class<*>;"
    array-length v0, p0

    array-length v1, p1

    const/4 v2, 0x0

    if-eq v0, v1, :cond_0

    return v2

    .line 42
    :cond_0
    const/4 v0, 0x0

    .local v0, "i":I
    :goto_0
    array-length v1, p0

    if-ge v0, v1, :cond_a

    .line 43
    aget-object v1, p0, v0

    invoke-virtual {v1}, Ljava/lang/Class;->isPrimitive()Z

    move-result v1

    if-eqz v1, :cond_8

    .line 44
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_1

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Integer;

    if-nez v1, :cond_1

    return v2

    .line 45
    :cond_1
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Byte;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_2

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Byte;

    if-nez v1, :cond_2

    return v2

    .line 46
    :cond_2
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Character;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_3

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Character;

    if-nez v1, :cond_3

    return v2

    .line 47
    :cond_3
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_4

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Boolean;

    if-nez v1, :cond_4

    return v2

    .line 48
    :cond_4
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Double;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_5

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Double;

    if-nez v1, :cond_5

    return v2

    .line 49
    :cond_5
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Float;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_6

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Float;

    if-nez v1, :cond_6

    return v2

    .line 50
    :cond_6
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Long;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_7

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Long;

    if-nez v1, :cond_7

    return v2

    .line 51
    :cond_7
    aget-object v1, p0, v0

    sget-object v3, Ljava/lang/Short;->TYPE:Ljava/lang/Class;

    if-ne v1, v3, :cond_9

    aget-object v1, p1, v0

    instance-of v1, v1, Ljava/lang/Short;

    if-nez v1, :cond_9

    return v2

    .line 52
    :cond_8
    aget-object v1, p1, v0

    if-eqz v1, :cond_9

    aget-object v1, p0, v0

    aget-object v3, p1, v0

    invoke-virtual {v1, v3}, Ljava/lang/Class;->isInstance(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_9

    return v2

    .line 42
    :cond_9
    add-int/lit8 v0, v0, 0x1

    goto/16 :goto_0

    .line 54
    .end local v0    # "i":I
    :cond_a
    const/4 v0, 0x1

    return v0
.end method

.method public static getCachedOffsetData()[J
    .locals 1

    .line 29
    sget-object v0, Lorg/lsposed/hiddenapibypass/Helper;->cachedOffsetData:[J

    return-object v0
.end method

.method public static setCachedOffsetData([J)V
    .locals 2
    .param p0, "data"    # [J

    .line 33
    sget-object v0, Lorg/lsposed/hiddenapibypass/Helper;->cachedOffsetData:[J

    if-nez v0, :cond_0

    array-length v0, p0

    const/4 v1, 0x6

    if-ne v0, v1, :cond_0

    .line 34
    sput-object p0, Lorg/lsposed/hiddenapibypass/Helper;->cachedOffsetData:[J

    .line 38
    return-void

    .line 36
    :cond_0
    new-instance v0, Ljava/lang/IllegalArgumentException;

    invoke-direct {v0}, Ljava/lang/IllegalArgumentException;-><init>()V

    throw v0
.end method
