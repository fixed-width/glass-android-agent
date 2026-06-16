package com.fixedwidth.glassagent

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolTest {
    @Test fun parses_ping() {
        assertEquals(Request.Ping(1), Protocol.parse("""{"id":1,"op":"ping"}"""))
    }
    @Test fun parses_clipboard_set_with_unicode() {
        assertEquals(Request.ClipboardSet(2, "héllo"), Protocol.parse("""{"id":2,"op":"clipboard_set","text":"héllo"}"""))
    }
    @Test fun parses_clipboard_get() {
        assertEquals(Request.ClipboardGet(3), Protocol.parse("""{"id":3,"op":"clipboard_get"}"""))
    }
    @Test fun parses_pointer_path() {
        val r = Protocol.parse("""{"id":4,"op":"pointer","gesture":[{"x":10,"y":20,"t_ms":0},{"x":10,"y":80,"t_ms":50}],"button":"left"}""")
        assertEquals(Request.Pointer(4, listOf(Pt(10,20,0), Pt(10,80,50)), "left"), r)
    }
    @Test fun parses_key_and_text() {
        assertEquals(Request.Key(5, "ctrl+a"), Protocol.parse("""{"id":5,"op":"key","chord":"ctrl+a"}"""))
        assertEquals(Request.Text(6, "hi"), Protocol.parse("""{"id":6,"op":"text","text":"hi"}"""))
    }
    @Test fun unknown_op_is_captured_with_id() {
        val r = Protocol.parse("""{"id":7,"op":"frobnicate"}""")
        assertEquals(Request.Unknown(7, "frobnicate"), r)
    }
    @Test fun serializes_ok_text_and_error() {
        // Field-by-field comparison: org.json key insertion order varies by JVM build
        val okText = JSONObject(Protocol.serialize(Response.okText(1, "x")))
        assertEquals(1, okText.getInt("id"))
        assertTrue(okText.getBoolean("ok"))
        assertEquals("x", okText.getString("text"))

        val ok = JSONObject(Protocol.serialize(Response.ok(2)))
        assertEquals(2, ok.getInt("id"))
        assertTrue(ok.getBoolean("ok"))
        assertFalse(ok.has("text"))
        assertFalse(ok.has("error"))

        val err = JSONObject(Protocol.serialize(Response.error(3, "nope")))
        assertEquals(3, err.getInt("id"))
        assertFalse(err.getBoolean("ok"))
        assertEquals("nope", err.getString("error"))
    }
    @Test fun hello_line_is_stable() {
        assertEquals("""{"hello":{"proto":1}}""", Protocol.helloLine())
    }
    @Test fun malformed_on_bad_json() {
        assertEquals(Request.Malformed("not json"), Protocol.parse("not json"))
    }
    @Test fun malformed_on_missing_id() {
        assertEquals(Request.Malformed("""{"op":"ping"}"""), Protocol.parse("""{"op":"ping"}"""))
    }
    @Test fun malformed_on_bad_pointer_element_does_not_throw() {
        // A pointer point missing "y" must degrade to Malformed, never throw.
        assertEquals(
            Request.Malformed("""{"id":4,"op":"pointer","gesture":[{"x":1}]}"""),
            Protocol.parse("""{"id":4,"op":"pointer","gesture":[{"x":1}]}""")
        )
    }
}
