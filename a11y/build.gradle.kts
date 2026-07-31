plugins {
    id("com.android.application") version "8.7.0"
    kotlin("android") version "2.0.21"
}

android {
    namespace = "com.fixedwidth.glassa11y"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fixedwidth.glassa11y"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.5.0"
    }

    // Debug-signed only, still no secret to manage — the credentials are the well-known
    // debug ones (alias androiddebugkey, password "android"). But the keystore is committed
    // rather than auto-generated per build, so the signature is stable across releases and
    // `adb install -r` can update in place instead of needing an uninstall first.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // Match Java + Kotlin targets (AGP defaults Java to 8; Kotlin is 17 → mismatch errors).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303") // org.json on the JVM test classpath
}
