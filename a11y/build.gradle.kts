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
        versionCode = 1
        versionName = "0.1.0"
    }

    // Debug-signed only — `adb install` accepts the auto-generated debug key, and this is
    // emulator dev tooling, so there is no release keystore / CI secret to manage.
    buildTypes {
        getByName("debug") { isMinifyEnabled = false }
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
