plugins {
    kotlin("jvm") version "2.0.21"
}

repositories { mavenCentral() }

// SDK location: prefer the standard env vars (overridable), else -PandroidSdkRoot, else fail clearly.
val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
    ?: System.getenv("ANDROID_HOME")
    ?: providers.gradleProperty("androidSdkRoot").orNull
    ?: error("Set ANDROID_SDK_ROOT (or ANDROID_HOME), or pass -PandroidSdkRoot=/path/to/android-sdk")
val buildTools = providers.gradleProperty("buildToolsVersion").get()
val compileSdk = providers.gradleProperty("compileSdk").get()
val androidJar = "$sdkRoot/platforms/android-$compileSdk/android.jar"

dependencies {
    // The Android framework (incl. org.json) is present at runtime under app_process,
    // so compile against it but never bundle it into the dex.
    compileOnly(files(androidJar))
    testImplementation(kotlin("test"))
    // org.json for JVM tests (android.jar is not on the test runtime classpath).
    testImplementation("org.json:json:20240303")
    // Android framework constants (e.g. MotionEvent.ACTION_*) are compile-time ints; add
    // android.jar to the test compile classpath so tests can import Android types directly.
    // It is NOT on the test runtime classpath — no Android classes are instantiated in tests.
    testCompileOnly(files(androidJar))
}

kotlin { jvmToolchain(17) }

// Produce build/glass-agent.jar: a jar whose only entry is classes.dex, containing our
// classes + kotlin-stdlib (everything on the runtime classpath, which excludes the
// compileOnly android.jar). app_process loads it via CLASSPATH.
val dex by tasks.registering {
    dependsOn(tasks.named("jar"))
    val d8 = "$sdkRoot/build-tools/$buildTools/d8"
    val out = layout.buildDirectory.file("glass-agent.jar")
    val classesJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val runtimeCp = configurations.named("runtimeClasspath")
    inputs.file(classesJar)
    inputs.files(runtimeCp)
    outputs.file(out)
    doLast {
        require(file(d8).exists()) {
            "d8 not found at $d8 — install build-tools;$buildTools via `sdkmanager`"
        }
        val outFile = out.get().asFile
        outFile.parentFile.mkdirs()
        val cmd = mutableListOf(d8, "--min-api", "24", "--release",
                                "--output", outFile.absolutePath,
                                classesJar.get().asFile.absolutePath)
        runtimeCp.get().files.forEach { cmd += it.absolutePath }
        project.exec { commandLine(cmd) }
    }
}
