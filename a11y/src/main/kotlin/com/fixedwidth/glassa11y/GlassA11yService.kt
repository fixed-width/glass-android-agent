package com.fixedwidth.glassa11y

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.net.LocalServerSocket
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.concurrent.thread

class GlassA11yService : AccessibilityService() {
    @Volatile private var server: LocalServerSocket? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        thread(name = "glass-a11y-server", isDaemon = true) { acceptLoop() }
    }

    private fun acceptLoop() {
        // The abstract socket name can already be bound if the service is rapidly restarted;
        // surface that instead of letting the daemon thread die silently with the socket closed.
        val srv = try {
            LocalServerSocket("glass-a11y")
        } catch (e: Exception) {
            System.err.println("glass-a11y: failed to open socket: ${e.message}")
            return
        }
        server = srv
        System.err.println("glass-a11y: listening on localabstract:glass-a11y")
        // Both `tree` and `action` target the current active window; a tree/action pair
        // from the host runs back-to-back against the same window.
        // One read, not two: fetching the package separately can straddle a window change and name a
        // window other than the one described.
        val source = TreeSource { _ ->
            // adapt() returns null only for a null argument; `it` is non-null inside this `let`.
            rootInActiveWindow?.let { ActiveWindow(adapt(it)!!, it.packageName?.toString()) }
        }
        while (true) {
            val client = try { srv.accept() } catch (e: Exception) { break }
            try {
                Server(source, liveSink()).serve(
                    client.inputStream.bufferedReader(), client.outputStream.bufferedWriter())
            } catch (e: Exception) {
                System.err.println("glass-a11y: connection error: ${e.message}")
            } finally {
                runCatching { client.close() }
            }
        }
        runCatching { srv.close() }
        server = null
    }

    /**
     * ActionSink that reads the active window once — the same read is checked against `pkg` and
     * walked by `matchLive` — then re-finds the live node (by class + screen bounds) and performs
     * the action. Two separate reads would let the foreground change between them, so the check
     * would pass on stale evidence while the actuation lands in whatever took focus.
     */
    private fun liveSink() = ActionSink { node, pkg, action, text ->
        val root = rootInActiveWindow ?: error("no active window")
        requireLivePackage(pkg, root.packageName?.toString())
        val live = matchLive(root, node) ?: error("live node gone")
        performOn(live, action, text)
    }

    private fun matchLive(root: AccessibilityNodeInfo, want: NodeData): AccessibilityNodeInfo? {
        val wb = want.bounds
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val r = Rect().also { n.getBoundsInScreen(it) }
            if (n.className?.toString() == want.className &&
                r.left == wb.x && r.top == wb.y && r.width() == wb.w && r.height() == wb.h) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it)?.let { h -> return h } }
            return null
        }
        return walk(root)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        runCatching { server?.close() }
        return super.onUnbind(intent)
    }
}
