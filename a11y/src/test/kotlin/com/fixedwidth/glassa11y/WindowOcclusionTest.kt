package com.fixedwidth.glassa11y

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowOcclusionTest {
    private val target = WindowAtPoint(10, 0, 1, true, true)
    private val cover = WindowAtPoint(20, 0, 3, true, true)

    @Test fun higher_touchable_window_covers_target() {
        assertEquals(20, coveringWindow(10, listOf(target, cover)))
    }

    @Test fun highest_cover_is_reported() {
        assertEquals(30, coveringWindow(10, listOf(cover, target, cover.copy(id=30, layer=5))))
    }

    @Test fun clear_window_never_proves_a_control_unobstructed() {
        assertNull(coveringWindow(10, listOf(target)))
    }

    @Test fun other_displays_lower_layers_and_holes_do_not_block() {
        for (other in listOf(cover.copy(displayId=1), cover.copy(layer=0),
            cover.copy(layer=1), cover.copy(containsPoint=false), cover.copy(eligibleCover=false))) {
            assertNull(coveringWindow(10, listOf(target, other)), "$other")
        }
    }

    @Test fun missing_duplicate_invalid_or_off_display_target_is_inconclusive() {
        for (windows in listOf(listOf(cover), listOf(target, target, cover),
            listOf(target.copy(displayId=1), cover.copy(displayId=1)),
            listOf(target.copy(containsPoint=false), cover), listOf(target, cover.copy(id=-1)))) {
            assertNull(coveringWindow(10, windows), "$windows")
        }
    }

    @Test fun point_request_requires_two_integer_coordinates_without_coercion() {
        for (point in listOf("null", "{}", "{\"x\":1}", "{\"x\":1.5,\"y\":2}",
            "{\"x\":\"1\",\"y\":2}", "{\"x\":2147483648,\"y\":2}")) {
            assertTrue(Protocol.parse("""{"id":1,"op":"tree","pointer_point":$point}""") is Request.Malformed)
        }
        assertEquals(Request.Tree(1, "app", ScreenPoint(-12, 34)), Protocol.parse(
            """{"id":1,"op":"tree","package":"app","pointer_point":{"x":-12,"y":34}}"""))
    }

    @Test fun response_pairs_tree_with_the_point_and_window_identity() {
        val evidence = PointerWindow(ScreenPoint(12, 34), 10, 0, 20)
        val response = Response.okTree(1, "{}", "app").copy(pointerWindow=evidence)
        val json = JSONObject(Protocol.serialize(response)).getJSONObject("pointer_window")
        assertEquals(1, json.getInt("version"))
        assertEquals(12, json.getInt("x"))
        assertEquals(34, json.getInt("y"))
        assertEquals(10, json.getInt("window_id"))
        assertEquals(0, json.getInt("display_id"))
        assertEquals(20, json.getInt("occluding_window_id"))
        val clear = JSONObject(Protocol.serialize(response.copy(pointerWindow=evidence.copy(occludingWindowId=null))))
        assertTrue(!clear.getJSONObject("pointer_window").has("occluding_window_id"))
    }
}
