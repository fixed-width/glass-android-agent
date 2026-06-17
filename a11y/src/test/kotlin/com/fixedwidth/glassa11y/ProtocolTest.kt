package com.fixedwidth.glassa11y

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTest {
    @Test fun hello_announces_proto() {
        assertEquals(1, JSONObject(Protocol.helloLine()).getJSONObject("hello").getInt("proto"))
    }

    @Test fun parses_each_op() {
        assertTrue(Protocol.parse("""{"id":1,"op":"ping"}""") is Request.Ping)
        val t = Protocol.parse("""{"id":2,"op":"tree","package":"com.x"}""")
        assertTrue(t is Request.Tree && t.pkg == "com.x")
        val a = Protocol.parse("""{"id":3,"op":"action","ref":5,"action":"click"}""")
        assertTrue(a is Request.Action && a.ref == 5 && a.action == "click" && a.text == null)
        val s = Protocol.parse("""{"id":4,"op":"action","ref":6,"action":"set_text","text":"hi"}""")
        assertTrue(s is Request.Action && s.action == "set_text" && s.text == "hi")
    }

    @Test fun id_less_or_bad_json_is_malformed_sentinel() {
        assertEquals(-1, Protocol.parse("not json").id)
        assertEquals(-1, Protocol.parse("""{"op":"ping"}""").id)
    }

    @Test fun serializes_ok_tree_and_error() {
        assertEquals("""{"id":1,"ok":true}""", Protocol.serialize(Response.ok(1)))
        val tree = Protocol.serialize(Response.okTree(2, """{"ref":0}"""))
        val o = JSONObject(tree)
        assertEquals(0, o.getJSONObject("tree").getInt("ref"))
        assertTrue(o.getBoolean("ok"))
        assertEquals("nope", JSONObject(Protocol.serialize(Response.error(3, "nope"))).getString("error"))
    }
}
