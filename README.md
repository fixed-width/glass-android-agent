# glass-android-agent

A companion on-device agent for [glass](https://github.com/fixed-width/glass), the
developer automation tool. The agent runs under `app_process` on an Android device or
emulator and exposes a `localabstract` Unix socket that glass drives over ADB port
forwarding.

## What it does

The agent listens on a `localabstract` Unix-domain socket (`glass-agent`) and speaks a
line-delimited JSON protocol; the host reaches it via `adb forward tcp:<port> localabstract:glass-agent`. It provides:

- **Clipboard access** — get and set the system clipboard across processes.
- **Input injection** — pointer gestures (tap, swipe), key events, and text entry via
  Android's `InputManager` platform API, available to a shell-uid `app_process` process.

## Download

Prebuilt `glass-agent.jar` artifacts are published on the
[Releases](https://github.com/fixed-width/glass-android-agent/releases) page — download the
jar (and verify it against the accompanying `glass-agent.jar.sha256`) and skip the build
toolchain entirely. Each release is built by CI from the matching tag. Build from source
(below) only if you want to modify the agent.

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

The agent prints `glass-agent: listening on localabstract:glass-agent` to stderr when ready.

Forward the socket from the host (use tcp:0 to let the OS pick a free port):

```bash
adb forward tcp:0 localabstract:glass-agent
PORT=$(adb forward --list | awk '/glass-agent/ {sub(/tcp:/,"",$2); print $2; exit}')
```

Connections are sequential — each client is served to completion before the next is
accepted. Connect with any line-oriented TCP client (e.g. `nc 127.0.0.1 $PORT`).

## Protocol

Line-delimited JSON. On connect the agent sends a hello banner:

```json
{"hello":{"proto":1}}
```

All subsequent requests are JSON objects sent by the client, one per line, and each
receives a one-line JSON response:

| `op`            | Required fields                                   | Response fields          |
|-----------------|---------------------------------------------------|--------------------------|
| `ping`          | `id`                                              | `id`, `ok:true`          |
| `clipboard_get` | `id`                                              | `id`, `ok:true`, `text`  |
| `clipboard_set` | `id`, `text`                                      | `id`, `ok:true`          |
| `pointer`       | `id`, `gesture` (array of `{x,y,t_ms}`), `button` (accepted, currently ignored — reserved for future use) | `id`, `ok:true`          |
| `key`           | `id`, `chord` (`"+"‑joined modifier+key string, e.g. `ctrl+a`, `shift+tab`, `enter`, `f5`) | `id`, `ok:true`          |
| `text`          | `id`, `text`                                      | `id`, `ok:true`          |

Every request receives exactly one response line; the host correlates responses to
requests by `id`. On a handler error or unknown op, the agent replies
`{"id":<id>,"ok":false,"error":"..."}` and **keeps the connection open** — the session
continues normally. If a line cannot be parsed at all (malformed JSON or missing `id`),
the agent replies with `{"id":-1,"ok":false,"error":"malformed request"}`; a host should
treat an `id` it did not send (e.g. `-1`) as a protocol error and reconnect.

### Example session

```
→ connect
← {"hello":{"proto":1}}
→ {"id":1,"op":"ping"}
← {"id":1,"ok":true}
→ {"id":2,"op":"clipboard_set","text":"hello"}
← {"id":2,"ok":true}
→ {"id":3,"op":"clipboard_get"}
← {"id":3,"ok":true,"text":"hello"}
→ {"id":4,"op":"pointer","gesture":[{"x":540,"y":960,"t_ms":0}],"button":"left"}
← {"id":4,"ok":true}
→ {"id":5,"op":"text","text":"abc"}
← {"id":5,"ok":true}
→ {"id":6,"op":"key","chord":"ctrl+a"}
← {"id":6,"ok":true}
```

## Integration with glass

glass locates the agent jar via the `GLASS_ANDROID_AGENT_JAR` environment variable,
which should point to `build/glass-agent.jar`. glass handles push, launch, and
`adb forward` automatically before handing off to the MCP tools.

## License

Apache-2.0. See [LICENSE](LICENSE).
