package com.fixedwidth.glassagent

import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

class SystemInput : Input {
    private val im: Any
    private val injectMethod: java.lang.reflect.Method
    // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2
    private val modeWait = 2

    init {
        // Android 14 (API 34) moved injectInputEvent to InputManagerGlobal; older releases
        // have it on InputManager. Try the new home first, fall back to the old one.
        val (obj, cls) = try {
            val g = Class.forName("android.hardware.input.InputManagerGlobal")
            g.getMethod("getInstance").invoke(null)!! to g
        } catch (e: Throwable) {
            val m = Class.forName("android.hardware.input.InputManager")
            m.getMethod("getInstance").invoke(null)!! to m
        }
        im = obj
        injectMethod = cls.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }

    private fun inject(ev: InputEvent) { injectMethod.invoke(im, ev, modeWait) }

    data class Frame(val action: Int, val eventTimeMs: Long, val coords: List<Pt>)

    override fun gesture(paths: List<List<Pt>>) {
        if (paths.size < 2) return
        val down = SystemClock.uptimeMillis()
        val n = paths.size
        val props = Array(n) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        for (f in planGesture(paths)) {
            val pc = f.coords.map { p ->
                MotionEvent.PointerCoords().apply { x = p.x.toFloat(); y = p.y.toFloat(); pressure = 1f; size = 1f }
            }.toTypedArray()
            val ev = MotionEvent.obtain(
                down, down + f.eventTimeMs, f.action, pc.size, props.copyOfRange(0, pc.size), pc,
                0, 0, 1f, 1f, 0, 0, 0x1002 /*SOURCE_TOUCHSCREEN*/, 0,
            )
            try { inject(ev) } finally { ev.recycle() }
        }
    }

    override fun pointer(path: List<Pt>, button: String) {
        // `button` is reserved for future tap-vs-secondary mapping; v1 injects touch only.
        if (path.isEmpty()) return
        val down = SystemClock.uptimeMillis()
        val first = path.first()
        injectMotion(MotionEvent.ACTION_DOWN, down, down, first.x, first.y)
        for (p in path.drop(1)) {
            injectMotion(MotionEvent.ACTION_MOVE, down, down + p.tMs, p.x, p.y)
        }
        val last = path.last()
        injectMotion(MotionEvent.ACTION_UP, down, down + last.tMs, last.x, last.y)
    }

    private fun injectMotion(action: Int, downTime: Long, eventTime: Long, x: Int, y: Int) {
        val ev = MotionEvent.obtain(downTime, eventTime, action, x.toFloat(), y.toFloat(), 0)
        ev.source = 0x1002 // SOURCE_TOUCHSCREEN
        try { inject(ev) } finally { ev.recycle() }
    }

    override fun key(chord: String) {
        val parts = chord.split("+")
        val meta = parts.dropLast(1).fold(0) { m, name -> m or metaOf(name) }
        val code = keycodeOf(parts.last())
        val t = SystemClock.uptimeMillis()
        inject(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        inject(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    override fun text(s: String) {
        val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = kcm.getEvents(s.toCharArray())
        if (events != null) {
            events.forEach { inject(it) }
            return
        }
        // Some characters aren't on the virtual keymap; fall back to a string KeyEvent
        // (the deprecated "characters" constructor carries the literal text in one event).
        @Suppress("DEPRECATION")
        inject(KeyEvent(SystemClock.uptimeMillis(), s, KeyCharacterMap.VIRTUAL_KEYBOARD, 0))
    }

    private fun metaOf(name: String): Int = when (name.lowercase()) {
        "ctrl", "control" -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        "shift" -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        "alt" -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        "meta", "super" -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        else -> 0
    }

    private fun keycodeOf(name: String): Int =
        KEYS[name.lowercase()]
            ?: KeyEvent.keyCodeFromString("KEYCODE_${name.uppercase()}")
                .takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
            ?: KeyEvent.KEYCODE_UNKNOWN

    companion object {
        /** Plan the ordered multi-pointer frames from N time-aligned paths (equal length & timestamps). */
        fun planGesture(paths: List<List<Pt>>): List<Frame> {
            val n = paths.size
            val steps = paths[0].size
            val frames = ArrayList<Frame>()
            fun coordsAt(step: Int, count: Int) = (0 until count).map { paths[it][step] }
            frames.add(Frame(MotionEvent.ACTION_DOWN, paths[0][0].tMs, coordsAt(0, 1)))
            for (i in 1 until n) {
                val action = MotionEvent.ACTION_POINTER_DOWN or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                frames.add(Frame(action, paths[0][0].tMs, coordsAt(0, i + 1)))
            }
            for (s in 1 until steps) {
                frames.add(Frame(MotionEvent.ACTION_MOVE, paths[0][s].tMs, coordsAt(s, n)))
            }
            val last = steps - 1
            for (i in n - 1 downTo 1) {
                val action = MotionEvent.ACTION_POINTER_UP or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                frames.add(Frame(action, paths[0][last].tMs, coordsAt(last, i + 1)))
            }
            frames.add(Frame(MotionEvent.ACTION_UP, paths[0][last].tMs, coordsAt(last, 1)))
            return frames
        }

        // Minimal named-key map; single letters/digits resolve via KEYCODE_<X>.
        val KEYS: Map<String, Int> = mapOf(
            "enter" to KeyEvent.KEYCODE_ENTER,
            "return" to KeyEvent.KEYCODE_ENTER,
            "tab" to KeyEvent.KEYCODE_TAB,
            "space" to KeyEvent.KEYCODE_SPACE,
            "backspace" to KeyEvent.KEYCODE_DEL,
            "delete" to KeyEvent.KEYCODE_FORWARD_DEL,
            "escape" to KeyEvent.KEYCODE_ESCAPE,
            "home" to KeyEvent.KEYCODE_MOVE_HOME,
            "end" to KeyEvent.KEYCODE_MOVE_END,
        )
    }
}
