package com.samsung.android.honeyboard.app;

import android.app.Application;

/**
 * Compile-time bridge for the locally supplied Samsung HoneyBoard APK.
 * The merge step removes this stub from the Nemotron dex and injects the
 * actual (re-opened) HoneyBoardApplication implementation from that APK.
 */
public class HoneyBoardApplication extends Application {
}
