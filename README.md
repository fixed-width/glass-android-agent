# glass-android-agent

On-device companions for [glass](https://github.com/fixed-width/glass), the developer
automation tool. They extend what glass can do on an Android device/emulator beyond what
plain `adb` allows. Both are **optional** — glass works without them and falls back to its
`adb`-only paths — and glass installs, launches, connects, and tears each one down for you.

This repo ships **two** components (a Gradle multi-module build):

| Module | Artifact | Runs as | Gives glass |
|--------|----------|---------|-------------|
| `:agent` | `glass-agent.jar` | `app_process` (shell uid) | clipboard get/set + high-fidelity input (real `MotionEvent`/`KeyEvent`, faithful Unicode) |
| `:a11y`  | `glass-a11y.apk`  | an installed `AccessibilityService` | a Compose-rich accessibility tree + high-fidelity `set_value` (`ACTION_SET_TEXT`) |

(`:fixture-compose` is a tiny Compose app used only by the on-device tests; it is not a
released artifact.)

Each speaks a line-delimited JSON protocol over a `localabstract` Unix socket that glass
reaches via `adb forward tcp:<port> localabstract:<name>`.

## Download

Prebuilt artifacts are published on the
[Releases](https://github.com/fixed-width/glass-android-agent/releases) page — download the
`glass-agent.jar` and/or `glass-a11y.apk` (and verify against the accompanying `.sha256`) and
skip the build toolchain entirely. Each release is built by CI from the matching tag. Build
from source (below) only to modify a component.

## Building

**Prerequisites:**

- JDK 17 or later (the Gradle Kotlin toolchain requires a full JDK, not just a JRE).
- Android SDK with `platforms;android-34` and `build-tools;34.0.0` installed.

Set the SDK location via the standard env var, then build the module you want:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk    # or ANDROID_HOME

./gradlew :agent:dex              # -> agent/build/glass-agent.jar (a dexed jar)
./gradlew :a11y:assembleDebug     # -> a11y/build/outputs/apk/debug/a11y-debug.apk
```

The APK is debug-signed (the auto-generated debug key) — `adb install` accepts it, and this is
emulator dev tooling, so there is no release keystore to manage.

## The agent (`:agent`) — clipboard + input

Runs under `app_process` as the shell uid and listens on `localabstract:glass-agent`. It
provides:

- **Clipboard access** — get/set the system clipboard across processes.
- **Input injection** — pointer gestures (tap, swipe), key events, and text entry via Android's
  `InputManager`, available to a shell-uid process (faithful Unicode, unlike `adb shell input`).

glass runs it for you; to drive it by hand for testing:

```bash
adb push agent/build/glass-agent.jar /data/local/tmp/glass-agent.jar
adb shell "CLASSPATH=/data/local/tmp/glass-agent.jar app_process / com.fixedwidth.glassagent.Main"
adb forward tcp:0 localabstract:glass-agent     # then connect with nc 127.0.0.1 <port>
```

### Agent protocol

Line-delimited JSON. On connect the agent sends `{"hello":{"proto":1}}`, then answers one
response line per request line:

| `op`            | Required fields                                   | Response fields          |
|-----------------|---------------------------------------------------|--------------------------|
| `ping`          | `id`                                              | `id`, `ok:true`          |
| `clipboard_get` | `id`                                              | `id`, `ok:true`, `text`  |
| `clipboard_set` | `id`, `text`                                      | `id`, `ok:true`          |
| `pointer`       | `id`, `gesture` (array of `{x,y,t_ms}`), `button` (accepted, reserved) | `id`, `ok:true` |
| `key`           | `id`, `chord` (`"+"`-joined, e.g. `ctrl+a`, `shift+tab`, `enter`, `f5`) | `id`, `ok:true` |
| `text`          | `id`, `text`                                      | `id`, `ok:true`          |

On a handler error or unknown op, the agent replies `{"id":<id>,"ok":false,"error":"..."}` and
keeps the connection open. An unparseable line (bad JSON / missing `id`) gets
`{"id":-1,"ok":false,"error":"malformed request"}`; a host should treat an `id` it did not send
as a protocol error and reconnect.

## The a11y service (`:a11y`) — accessibility tree + `set_value`

An installed `AccessibilityService`. Reading the live `AccessibilityNodeInfo` tree surfaces
**Jetpack Compose semantics** that `uiautomator` tends to flatten, and `ACTION_SET_TEXT` sets
editable fields directly. Because an `AccessibilityService` is system-bound, it must be an
*installed* APK and enabled in secure settings — glass does both (`pm install` +
`settings put secure enabled_accessibility_services …`) and restores the device's prior state
on teardown.

It listens on `localabstract:glass-a11y` and sends the same `{"hello":{"proto":1}}` banner.

| `op`     | Required fields                                    | Response fields                       |
|----------|----------------------------------------------------|---------------------------------------|
| `ping`   | `id`                                               | `id`, `ok:true`                       |
| `tree`   | `id`, `package` (serves the active window regardless) | `id`, `ok:true`, `tree` (a node object) |
| `action` | `id`, `ref`, `action` (`"set_text"` \| `"click"`), `text` (for `set_text`) | `id`, `ok:true` |

A tree **node** is `{"ref":N, "class":…, "text"?:…, "desc"?:…, "bounds":{"x","y","w","h"}, "editable":bool, "clickable":bool, "enabled":bool, "scrollable":bool, "children"?:[…]}`. `ref`
is a pre-order index (root = 0) the host uses to address a node in an `action`.

**Scope note:** glass uses this service for the **tree** and **`set_text`** only. It also
implements `click` (`ACTION_CLICK`), but glass does **not** route element clicks through it —
`ACTION_CLICK` is unreliable on Compose (returns success but no-ops), so glass clicks by
coordinate tap on the node's bounds instead.

### Example (a11y) session

```
→ connect
← {"hello":{"proto":1}}
→ {"id":1,"op":"tree","package":"com.example.app"}
← {"id":1,"ok":true,"tree":{"ref":0,"class":"android.widget.FrameLayout","bounds":{...},"children":[...]}}
→ {"id":2,"op":"action","ref":3,"action":"set_text","text":"hello"}
← {"id":2,"ok":true}
```

## Integration with glass

glass discovers each component by env var and handles install/launch/`adb forward`/teardown
automatically:

| Env var | Points at | Enables | Disable with |
|---------|-----------|---------|--------------|
| `GLASS_ANDROID_AGENT_JAR` | `glass-agent.jar` | clipboard + high-fidelity input | `GLASS_ANDROID_AGENT=off` |
| `GLASS_ANDROID_A11Y_APK`  | `glass-a11y.apk`  | Compose-rich a11y tree + `set_value` | `GLASS_ANDROID_A11Y=off` |

Run `GLASS_BACKEND=android glass-mcp doctor --deep` to check both (it installs, enables, and
pings each, then tears them down). See the Android section of glass's host guides for the full
setup.

## License

Apache-2.0. See [LICENSE](LICENSE).
