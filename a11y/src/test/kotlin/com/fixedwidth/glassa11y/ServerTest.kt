package com.fixedwidth.glassa11y

import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    private val sampleTree = NodeData(
        "android.widget.FrameLayout", null, null, Bounds(0, 0, 100, 100),
        false, false, true, false,
        listOf(NodeData("android.widget.Button", null, "Save", Bounds(1, 2, 8, 8),
            false, true, true, false, emptyList())),
    )

    private fun run(input: String, source: TreeSource, sink: ActionSink): List<JSONObject> {
        val out = StringWriter()
        Server(source, sink).serve(BufferedReader(StringReader(input)), out)
        // First line is hello; the rest are responses.
        return out.toString().trim().lines().drop(1).map { JSONObject(it) }
    }

    @Test fun ping_then_tree_returns_the_tree() {
        val resps = run(
            """{"id":1,"op":"ping"}""" + "\n" + """{"id":2,"op":"tree","package":"com.x"}""" + "\n",
            source = { pkg -> if (pkg == "com.x") sampleTree else null },
            sink = { _, _, _ -> },
        )
        assertTrue(resps[0].getBoolean("ok"))
        assertEquals("Save", resps[1].getJSONObject("tree").getJSONArray("children")
            .getJSONObject(0).getString("desc"))
    }

    @Test fun tree_for_absent_package_errors() {
        val resps = run("""{"id":1,"op":"tree","package":"com.gone"}""" + "\n",
            source = { null }, sink = { _, _, _ -> })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("no window"))
    }

    @Test fun action_dispatches_to_the_sink() {
        var got: Triple<NodeData, String, String?>? = null
        val resps = run("""{"id":1,"op":"action","ref":1,"action":"set_text","text":"hi"}""" + "\n",
            source = { sampleTree },
            sink = { node, action, text -> got = Triple(node, action, text) })
        assertTrue(resps[0].getBoolean("ok"))
        assertEquals("Save", got!!.first.contentDescription) // ref 1 = the Button
        assertEquals("set_text", got!!.second)
        assertEquals("hi", got!!.third)
    }

    @Test fun action_on_bad_ref_errors() {
        val resps = run("""{"id":1,"op":"action","ref":99,"action":"click"}""" + "\n",
            source = { sampleTree }, sink = { _, _, _ -> })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("ref"))
    }

    @Test fun unknown_op_errors() {
        val resps = run("""{"id":1,"op":"frobnicate"}""" + "\n",
            source = { null }, sink = { _, _, _ -> })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("unknown op"))
    }
}
