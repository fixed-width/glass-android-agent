# glass-android-agent

A companion on-device agent for [glass](https://github.com/fixed-width/glass), the
developer automation tool. The agent runs under `app_process` on an Android device or
emulator and exposes a `localabstract` Unix socket that glass drives over ADB port
forwarding.

## What it does

The agent listens on a `localabstract` Unix-domain socket (`glass-agent`) and speaks a
line-delimited JSON protocol; the host reaches it via `adb forward tcp:<port> localabstract:glass-agent`. It provides input injection (pointer, key, text) and clipboard access using
Android platform APIs available to a shell-uid `app_process` process.

## Building

**Prerequisites:**

- JDK 17 or later (the Gradle Kotlin toolchain requires a full JDK, not just a JRE).
- Android SDK with `platforms;android-34` and `build-tools;34.0.0` installed.

**SDK location** — set one of the standard env vars before building:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk   # or ANDROID_HOME
./gradlew dex
```

Alternatively, pass it on the command line:

```bash
./gradlew dex -PandroidSdkRoot=/path/to/android-sdk
```

Output: `build/glass-agent.jar` — a jar containing `classes.dex`, ready to push and run.

## Deploy and run

```bash
adb push build/glass-agent.jar /data/local/tmp/glass-agent.jar
adb shell "CLASSPATH=/data/local/tmp/glass-agent.jar app_process / com.fixedwidth.glassagent.Main"
```

Then forward the socket from the host:

```bash
adb forward tcp:0 localabstract:glass-agent
```

## License

Apache-2.0. See [LICENSE](LICENSE).
