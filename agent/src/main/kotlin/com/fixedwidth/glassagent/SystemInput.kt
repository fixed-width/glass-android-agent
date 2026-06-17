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
