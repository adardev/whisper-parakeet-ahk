.class public final Lorg/lsposed/hiddenapibypass/Helper$Field;
.super Lorg/lsposed/hiddenapibypass/Helper$AccessibleObject;
.source "Helper.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lorg/lsposed/hiddenapibypass/Helper;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "Field"
.end annotation


# instance fields
.field private accessFlags:I

.field private artFieldIndex:I

.field private declaringClass:Lorg/lsposed/hiddenapibypass/Helper$Class;

.field private offset:I

.field private type:Lorg/lsposed/hiddenapibypass/Helper$Class;


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 108
    invoke-direct {p0}, Lorg/lsposed/hiddenapibypass/Helper$AccessibleObject;-><init>()V

    return-void
.end method
