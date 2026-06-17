package com.fixedwidth.glassa11y

import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeDataTest {
    private fun n(cls: String, text: String? = null, desc: String? = null,
                  editable: Boolean = false, clickable: Boolean = false,
                  children: List<NodeData> = emptyList()) =
        NodeData("android.widget.$cls", text, desc,
            Bounds(0, 0, 10, 10), editable, clickable, true, false, children)

    @Test fun maps_node_fields_and_assigns_preorder_refs() {
        val tree = n("FrameLayout", children = listOf(
            n("EditText", text = "Email", editable = true),
            n("Button", desc = "Save", clickable = true),
        ))
        val o = JSONObject(treeJson(tree))
        assertEquals(0, o.getInt("ref"))                 // root = pre-order 0
        val kids = o.getJSONArray("children")
        assertEquals(1, kids.getJSONObject(0).getInt("ref"))
        assertEquals("Email", kids.getJSONObject(0).getString("text"))
        assertTrue(kids.getJSONObject(0).getBoolean("editable"))
        assertEquals(2, kids.getJSONObject(1).getInt("ref"))
        assertEquals("android.widget.Button", kids.getJSONObject(1).getString("class"))
        assertTrue(kids.getJSONObject(1).getBoolean("clickable"))
        assertEquals(10, kids.getJSONObject(1).getJSONObject("bounds").getInt("w"))
    }

    @Test fun clickable_reflects_action_click_not_just_the_flag() {
        // Compose exposes a button's click via ACTION_CLICK, with isClickable() == false.
        assertTrue(isClickableNode(false, listOf(AccessibilityNodeInfo.ACTION_CLICK)))
        assertTrue(isClickableNode(true, emptyList()))
        assertFalse(isClickableNode(false, listOf(AccessibilityNodeInfo.ACTION_FOCUS)))
    }

    @Test fun finds_node_by_preorder_ref() {
        val tree = n("FrameLayout", children = listOf(n("EditText", text = "X"), n("Button")))
        assertEquals("X", nodeByRef(tree, 1)?.text)
        assertEquals(null, nodeByRef(tree, 99))
    }
}
