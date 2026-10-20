# HiddenApiBypass 6.2

Source: https://github.com/LSPosed/AndroidHiddenApiBypass

Artifact: `org.lsposed.hiddenapibypass:hiddenapibypass:6.2` from Maven Central.

AAR SHA-256: `dcdbd6fc8f70d722b06700cbe30888ded5d329bc3088d3ad92deddbdc8895db3`.

The 12 smali classes were generated from the unmodified AAR `classes.jar` using Android SDK D8 with `--min-api 29`, then disassembled with Apktool 2.12.1. The original Apache 2.0 license is included. No network download occurs at runtime.

The clone initializes exemptions for framework API packages inside its own process. This does not grant signature permissions, root access, or access to protected SystemUI services. See `docs/oneui-home-integration.md` for device validation and remaining limitations.
