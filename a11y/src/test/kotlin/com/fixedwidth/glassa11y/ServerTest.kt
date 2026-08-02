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
        false, false, true, false, false, false, null, null,
        listOf(NodeData("android.widget.Button", null, "Save", Bounds(1, 2, 8, 8),
            false, true, true, false, false, false, null, null, emptyList())),
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
            source = { pkg -> if (pkg == "com.x") ActiveWindow(sampleTree, "com.x") else null },
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
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"set_text","text":"hi"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { node, action, text -> got = Triple(node, action, text) },
        )
        assertTrue(resps[1].getBoolean("ok"))
        assertEquals("Save", got!!.first.contentDescription) // ref 1 = the Button
        assertEquals("set_text", got!!.second)
        assertEquals("hi", got!!.third)
    }

    @Test fun action_on_bad_ref_errors() {
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":99,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { _, _, _ -> },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        assertTrue(resps[1].getString("error").contains("ref"))
    }

    @Test fun unknown_op_errors() {
        val resps = run("""{"id":1,"op":"frobnicate"}""" + "\n",
            source = { null }, sink = { _, _, _ -> })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("unknown op"))
    }

    @Test fun tree_reply_names_the_window_it_answered_from() {
        val resps = run("""{"id":1,"op":"tree","package":"com.x"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.other") },
            sink = { _, _, _ -> })
        assertTrue(resps[0].getBoolean("ok"))
        assertEquals("com.other", resps[0].getString("package"))
    }

    @Test fun a_window_with_no_package_omits_the_field() {
        val resps = run("""{"id":1,"op":"tree","package":"com.x"}""" + "\n",
            source = { ActiveWindow(sampleTree, null) },
            sink = { _, _, _ -> })
        assertTrue(resps[0].getBoolean("ok"))
        assertTrue(!resps[0].has("package"))
    }

    @Test fun an_action_after_the_window_changed_apps_is_refused() {
        var acted = false
        var served = 0
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            // The first call serves com.x; by the action the foreground has changed.
            source = { ActiveWindow(sampleTree, if (served++ == 0) "com.x" else "com.dialog") },
            sink = { _, _, _ -> acted = true },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        assertTrue(resps[1].getString("error").contains("com.x"))
        assertTrue(resps[1].getString("error").contains("com.dialog"))
        assertTrue(!acted, "the sink must not be reached")
    }

    @Test fun an_action_on_the_same_app_still_actuates() {
        var acted = false
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { _, _, _ -> acted = true },
        )
        assertTrue(resps[1].getBoolean("ok"))
        assertTrue(acted)
    }

    @Test fun an_action_with_no_preceding_tree_is_refused() {
        var acted = false
        val resps = run("""{"id":1,"op":"action","ref":1,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { _, _, _ -> acted = true })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("no tree"))
        assertTrue(!acted)
    }

    @Test fun an_action_is_refused_when_the_window_is_unnamed() {
        var acted = false
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, null) },
            sink = { _, _, _ -> acted = true },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        assertTrue(!acted)
    }

    @Test fun an_action_is_refused_when_only_the_active_window_is_unnamed() {
        var acted = false
        var served = 0
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            // The tree was served by a named app; by the action, the foreground is unnamed
            // (e.g. a system dialog) — one side unknown must still refuse.
            source = { ActiveWindow(sampleTree, if (served++ == 0) "com.x" else null) },
            sink = { _, _, _ -> acted = true },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        // Pins the refusal to the unnamed-window branch, not the (also-true) mismatch branch below it.
        assertTrue(resps[1].getString("error").contains("unnamed"))
        assertTrue(!acted)
    }

    @Test fun an_action_is_refused_when_only_the_served_tree_was_unnamed() {
        var acted = false
        var served = 0
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            // The tree was served by an unnamed window; by the action, the foreground is
            // named — still refused, since the ref cannot be confirmed against that window.
            source = { ActiveWindow(sampleTree, if (served++ == 0) null else "com.x") },
            sink = { _, _, _ -> acted = true },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        assertTrue(resps[1].getString("error").contains("unnamed"))
        assertTrue(!acted)
    }
}
