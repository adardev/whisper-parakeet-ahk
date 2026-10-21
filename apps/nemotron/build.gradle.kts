plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

// Ordinary builds must target Core. The legacy integration input shares the
// UI package ID and must only be generated explicitly by the split pipeline.
val component = providers.gradleProperty("nemotronComponent").getOrElse("core")
require(component in setOf("core", "integrated")) {
    "nemotronComponent must be core or integrated"
}
val coreOnly = component == "core"

android {
    namespace = "com.nemotron.voiceime"
    compileSdk = 36

    defaultConfig {
        applicationId = if (coreOnly) "com.nemotron.core" else "com.nemotron.voiceime2"
        minSdk = 29
        targetSdk = if (coreOnly) 33 else 35
        versionCode = 5
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    sourceSets.getByName("main").assets.setSrcDirs(
        if (coreOnly) emptyList<Any>()
        else listOf(rootProject.file("apps/nemotron-keyboard/assets"))
    )
    if (coreOnly) {
        sourceSets.getByName("debug").manifest.srcFile("src/core/AndroidManifest.xml")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.24")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Samsung Health Data SDK v1.1.0 (AAR oficial en app/libs)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
