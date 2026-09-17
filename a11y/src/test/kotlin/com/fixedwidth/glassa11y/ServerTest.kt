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
        true, false, false, false, false, false, true, false, false, false, null, null,
        listOf(NodeData("android.widget.Button", null, "Save", Bounds(1, 2, 8, 8),
            true, false, true, false, false, true, true, false, false, false, null, null,
            emptyList())),
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
            sink = { _, _, _, _ -> },
        )
        assertTrue(resps[0].getBoolean("ok"))
        assertEquals("Save", resps[1].getJSONObject("tree").getJSONArray("children")
            .getJSONObject(0).getString("desc"))
    }

    @Test fun tree_errors_when_no_window_is_active() {
        // TreeSource ignores the requested package; a null result means no window is active at
        // all, so the message must not claim anything about the specific package that was asked for.
        val resps = run("""{"id":1,"op":"tree","package":"com.gone"}""" + "\n",
            source = { null }, sink = { _, _, _, _ -> })
        assertTrue(!resps[0].getBoolean("ok"))
        assertEquals("no active window", resps[0].getString("error"))
    }

    @Test fun action_dispatches_to_the_sink() {
        var got: Triple<NodeData, String, String?>? = null
        var gotPkg: String? = null
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"set_text","text":"hi"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { node, pkg, action, text -> got = Triple(node, action, text); gotPkg = pkg },
        )
        assertTrue(resps[1].getBoolean("ok"))
        assertEquals("Save", got!!.first.contentDescription) // ref 1 = the Button
        assertEquals("set_text", got!!.second)
        assertEquals("hi", got!!.third)
        assertEquals("com.x", gotPkg)
    }

    @Test fun action_on_bad_ref_errors() {
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":99,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { _, _, _, _ -> },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        assertTrue(resps[1].getString("error").contains("ref"))
    }

    @Test fun unknown_op_errors() {
        val resps = run("""{"id":1,"op":"frobnicate"}""" + "\n",
            source = { null }, sink = { _, _, _, _ -> })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("unknown op"))
    }

    @Test fun tree_reply_names_the_window_it_answered_from() {
        val resps = run("""{"id":1,"op":"tree","package":"com.x"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.other") },
            sink = { _, _, _, _ -> })
        assertTrue(resps[0].getBoolean("ok"))
        assertEquals("com.other", resps[0].getString("package"))
    }

    @Test fun a_window_with_no_package_omits_the_field() {
        val resps = run("""{"id":1,"op":"tree","package":"com.x"}""" + "\n",
            source = { ActiveWindow(sampleTree, null) },
            sink = { _, _, _, _ -> })
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
            sink = { _, _, _, _ -> acted = true },
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
            sink = { _, _, _, _ -> acted = true },
        )
        assertTrue(resps[1].getBoolean("ok"))
        assertTrue(acted)
    }

    @Test fun a_later_tree_rearms_the_gate_for_the_new_app() {
        // Catches a `Tree`-arm regression that only ever records the *first* served package (e.g.
        // `if (!servedAny) served = win.pkg`), which would pin the connection to com.x forever.
        // `run()` executes the whole script before returning, so a call counter — not a boolean
        // checked mid-list — is what can tell which action actuated.
        var actCount = 0
        var call = 0
        val pkgs = listOf("com.x", "com.dialog", "com.dialog", "com.dialog")
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n" +
                """{"id":3,"op":"tree","package":"com.dialog"}""" + "\n" +
                """{"id":4,"op":"action","ref":1,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, pkgs[call++]) },
            sink = { _, _, _, _ -> actCount++ },
        )
        assertTrue(!resps[1].getBoolean("ok"), "refused once com.dialog took the foreground")
        assertTrue(resps[3].getBoolean("ok"), "a fresh tree(com.dialog) re-arms the gate for the new app")
        assertEquals(1, actCount, "exactly the second, re-armed action actuates")
    }

    @Test fun an_action_with_no_preceding_tree_is_refused() {
        var acted = false
        val resps = run("""{"id":1,"op":"action","ref":1,"action":"click"}""" + "\n",
            source = { ActiveWindow(sampleTree, "com.x") },
            sink = { _, _, _, _ -> acted = true })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("no tree"))
        assertTrue(resps[0].getString("error").contains("send a tree"), "the message must say the remedy")
        assertTrue(!acted)
    }

    @Test fun no_preceding_tree_is_refused_before_reading_the_active_window() {
        // Hoisted ahead of the read: even with no active window either, a fresh connection must
        // report the protocol-misuse reason, "no tree has been served", not "no active window".
        var acted = false
        val resps = run("""{"id":1,"op":"action","ref":1,"action":"click"}""" + "\n",
            source = { null },
            sink = { _, _, _, _ -> acted = true })
        assertTrue(!resps[0].getBoolean("ok"))
        assertTrue(resps[0].getString("error").contains("no tree"))
        assertTrue(!acted)
    }

    @Test fun an_action_is_refused_when_only_the_active_window_is_unnamed() {
        var acted = false
        var served = 0
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            // The tree was served by a named app; by the action, the foreground is unnamed
            // (e.g. a system dialog) — this side is transient, so retrying can help.
            source = { ActiveWindow(sampleTree, if (served++ == 0) "com.x" else null) },
            sink = { _, _, _, _ -> acted = true },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        val error = resps[1].getString("error")
        assertTrue(error.contains("unnamed"))
        assertTrue(error.contains("transient"), "this side is recoverable by retrying")
        assertTrue(!acted)
    }

    @Test fun an_action_is_refused_when_only_the_served_tree_was_unnamed() {
        var acted = false
        val resps = run(
            """{"id":1,"op":"tree","package":"com.x"}""" + "\n" +
                """{"id":2,"op":"action","ref":1,"action":"click"}""" + "\n",
            // The tree served on this connection came from an unnamed window: this connection can
            // never confirm the ref's app, whatever the foreground looks like by the time of the
            // action — re-snapshotting will not fix it.
            source = { ActiveWindow(sampleTree, null) },
            sink = { _, _, _, _ -> acted = true },
        )
        assertTrue(!resps[1].getBoolean("ok"))
        val error = resps[1].getString("error")
        assertTrue(error.contains("unnamed"))
        assertTrue(error.contains("will not fix"), "this side is not recoverable by retrying")
        assertTrue(!acted)
    }

    @Test fun point_read_pairs_one_live_tree_with_evidence_and_never_acts() {
        var reads = 0
        var actions = 0
        val evidence = PointerWindow(ScreenPoint(12, 34), 10, 0, 20)
        val server = Server(
            source = { error("ordinary tree read must not replace the point tree") },
            sink = { _, _, _, _ -> actions++ },
            pointerSource = { point ->
                reads++
                assertEquals(evidence.point, point)
                ActiveWindow(sampleTree, "com.live", evidence)
            },
        )
        val response = server.handle(Request.Tree(1, "com.asked", evidence.point))
        assertTrue(response.ok)
        assertEquals("com.live", response.pkg)
        assertEquals(evidence, response.pointerWindow)
        assertEquals(1, reads)
        assertEquals(0, actions)
    }

    @Test fun unsupported_point_read_returns_the_tree_without_evidence() {
        val server = Server(source = { ActiveWindow(sampleTree, "com.x") },
            sink = { _, _, _, _ -> error("must not act") })
        val response = server.handle(Request.Tree(1, "com.x", ScreenPoint(1, 2)))
        assertTrue(response.ok)
        assertEquals(null, response.pointerWindow)
    }

    @Test fun missing_point_tree_does_not_fall_back_to_another_read() {
        val server = Server(source = { error("no fallback") },
            sink = { _, _, _, _ -> error("must not act") }, pointerSource = { null })
        val response = server.handle(Request.Tree(1, "com.x", ScreenPoint(1, 2)))
        assertTrue(!response.ok)
        assertEquals("no active window", response.error)
    }
}
