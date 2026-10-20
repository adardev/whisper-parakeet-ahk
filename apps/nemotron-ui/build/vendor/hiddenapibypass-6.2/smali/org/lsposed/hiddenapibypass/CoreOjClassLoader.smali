.class final Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;
.super Ldalvik/system/PathClassLoader;
.source "CoreOjClassLoader.java"


# static fields
.field static final synthetic $assertionsDisabled:Z


# instance fields
.field private final useHelperStubs:Z


# direct methods
.method static constructor <clinit>()V
    .locals 0

    .line 13
    return-void
.end method

.method constructor <init>()V
    .locals 3

    .line 28
    invoke-static {}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;->getCoreOjPath()Ljava/lang/String;

    move-result-object v0

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-direct {p0, v0, v1, v2}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;-><init>(Ljava/lang/String;Ljava/lang/ClassLoader;Z)V

    .line 29
    return-void
.end method

.method constructor <init>(Ljava/lang/String;)V
    .locals 2
    .param p1, "path"    # Ljava/lang/String;

    .line 32
    const/4 v0, 0x0

    const/4 v1, 0x1

    invoke-direct {p0, p1, v0, v1}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;-><init>(Ljava/lang/String;Ljava/lang/ClassLoader;Z)V

    .line 33
    return-void
.end method

.method constructor <init>(Ljava/lang/String;Ljava/lang/ClassLoader;)V
    .locals 1
    .param p1, "path"    # Ljava/lang/String;
    .param p2, "parent"    # Ljava/lang/ClassLoader;

    .line 36
    const/4 v0, 0x0

    invoke-direct {p0, p1, p2, v0}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;-><init>(Ljava/lang/String;Ljava/lang/ClassLoader;Z)V

    .line 37
    return-void
.end method

.method private constructor <init>(Ljava/lang/String;Ljava/lang/ClassLoader;Z)V
    .locals 0
    .param p1, "path"    # Ljava/lang/String;
    .param p2, "parent"    # Ljava/lang/ClassLoader;
    .param p3, "useHelperStubs"    # Z

    .line 40
    invoke-direct {p0, p1, p2}, Ldalvik/system/PathClassLoader;-><init>(Ljava/lang/String;Ljava/lang/ClassLoader;)V

    .line 41
    iput-boolean p3, p0, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;->useHelperStubs:Z

    .line 42
    return-void
.end method

.method static getBootClassPath()Ljava/lang/String;
    .locals 2

    .line 18
    const-string v0, "java.boot.class.path"

    const-string v1, ""

    invoke-static {v0, v1}, Ljava/lang/System;->getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 19
    .local v0, "bootClassPath":Ljava/lang/String;
    nop

    .line 20
    return-object v0
.end method

.method private static getCoreOjPath()Ljava/lang/String;
    .locals 3

    .line 24
    invoke-static {}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;->getBootClassPath()Ljava/lang/String;

    move-result-object v0

    const-string v1, ":"

    const/4 v2, 0x2

    invoke-virtual {v0, v1, v2}, Ljava/lang/String;->split(Ljava/lang/String;I)[Ljava/lang/String;

    move-result-object v0

    const/4 v1, 0x0

    aget-object v0, v0, v1

    return-object v0
.end method


# virtual methods
.method public loadClass(Ljava/lang/String;)Ljava/lang/Class;
    .locals 1
    .param p1, "name"    # Ljava/lang/String;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            ")",
            "Ljava/lang/Class<",
            "*>;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/ClassNotFoundException;
        }
    .end annotation

    .line 46
    const-class v0, Ljava/lang/Object;

    invoke-virtual {v0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 47
    const-class v0, Ljava/lang/Object;

    return-object v0

    .line 50
    :cond_0
    :try_start_0
    invoke-virtual {p0, p1}, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;->findClass(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0
    :try_end_0
    .catch Ljava/lang/ClassNotFoundException; {:try_start_0 .. :try_end_0} :catch_0

    return-object v0

    .line 51
    :catch_0
    move-exception v0

    .line 54
    iget-boolean v0, p0, Lorg/lsposed/hiddenapibypass/CoreOjClassLoader;->useHelperStubs:Z

    if-nez v0, :cond_1

    .line 55
    invoke-super {p0, p1}, Ldalvik/system/PathClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    return-object v0

    .line 57
    :cond_1
    const-class v0, Ljava/lang/reflect/Executable;

    invoke-virtual {v0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    .line 58
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$Executable;

    return-object v0

    .line 59
    :cond_2
    const-class v0, Ljava/lang/invoke/MethodHandle;

    invoke-virtual {v0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_3

    .line 60
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$MethodHandle;

    return-object v0

    .line 61
    :cond_3
    const-class v0, Ljava/lang/Class;

    invoke-virtual {v0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_4

    .line 62
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$Class;

    return-object v0

    .line 63
    :cond_4
    const-class v0, Ljava/lang/reflect/Field;

    invoke-virtual {v0}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_5

    .line 64
    const-class v0, Lorg/lsposed/hiddenapibypass/Helper$Field;

    return-object v0

    .line 66
    :cond_5
    invoke-super {p0, p1}, Ldalvik/system/PathClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    return-object v0
.end method
