package com.fixedwidth.glassagent

import android.view.MotionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemInputTest {
    @Test fun named_keys_present() {
        assertTrue(SystemInput.KEYS.containsKey("enter"))
        assertTrue(SystemInput.KEYS.containsKey("backspace"))
    }

    @Test fun gesture_frames_open_move_close_in_order() {
        val paths = listOf(
            listOf(Pt(0, 0, 0), Pt(10, 0, 50)),
            listOf(Pt(0, 5, 0), Pt(0, 5, 50)),
        )
        val frames = SystemInput.planGesture(paths)
        // DOWN(p0), POINTER_DOWN(p1), MOVE(step1), POINTER_UP(p1), UP(p0) → 5 frames
        assertEquals(5, frames.size)
        assertEquals(MotionEvent.ACTION_DOWN, frames[0].action)
        assertEquals(0L, frames[0].eventTimeMs)
        assertEquals(1, frames[0].coords.size)
        assertTrue(frames[1].action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_DOWN)
        assertEquals(2, frames[1].coords.size)
        assertEquals(MotionEvent.ACTION_MOVE, frames[2].action)
        assertEquals(50L, frames[2].eventTimeMs)
        assertEquals(10, frames[2].coords[0].x)
        assertTrue(frames[3].action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_UP)
        assertEquals(MotionEvent.ACTION_UP, frames[4].action)
    }
}
