package com.fixedwidth.glassa11y

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeDataTest {
    private fun n(cls: String, text: String? = null, desc: String? = null,
                  editable: Boolean = false, clickable: Boolean = false,
                  checkable: Boolean = false, checked: Boolean = false,
                  resourceId: String? = null, hint: String? = null,
                  children: List<NodeData> = emptyList()) =
        NodeData("android.widget.$cls", text, desc,
            Bounds(0, 0, 10, 10), editable, clickable, true, false, checkable, checked,
            resourceId, hint, children)

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

    @Test fun emits_checkable_and_checked() {
        val o = JSONObject(treeJson(n("CheckBox", checkable = true, checked = true)))
        assertTrue(o.getBoolean("checkable"))
        assertTrue(o.getBoolean("checked"))
        val plain = JSONObject(treeJson(n("TextView")))
        assertFalse(plain.getBoolean("checkable"))
        assertFalse(plain.getBoolean("checked"))
    }

    @Test fun emits_resource_id_and_hint_when_present() {
        val o = JSONObject(treeJson(n("EditText", resourceId = "com.x:id/email", hint = "Email")))
        assertEquals("com.x:id/email", o.getString("resource_id"))
        assertEquals("Email", o.getString("hint"))
    }

    @Test fun omits_resource_id_and_hint_when_absent() {
        // An already-installed companion (or a pre-API-26 device for hint) sends neither key —
        // absent must stay an omitted key, not a JSON null.
        val o = JSONObject(treeJson(n("EditText")))
        assertFalse(o.has("resource_id"))
        assertFalse(o.has("hint"))
    }

    @Test fun clickable_reflects_action_click_not_just_the_flag() {
        // Compose exposes a button's click via ACTION_CLICK, with isClickable() == false.
        assertTrue(isClickableNode(false, listOf(AccessibilityNodeInfo.ACTION_CLICK)))
        assertTrue(isClickableNode(true, emptyList()))
        assertFalse(isClickableNode(false, listOf(AccessibilityNodeInfo.ACTION_FOCUS)))
    }

    @Test fun hint_for_reports_hint_at_or_above_api_26() {
        assertEquals("Email", hintFor(Build.VERSION_CODES.O, "Email"))
        assertEquals("Email", hintFor(Build.VERSION_CODES.O + 1, "Email"))
    }

    @Test fun hint_for_reports_null_below_api_26() {
        // getHintText() itself isn't called this low (adapt() guards it); this pins the boundary.
        assertEquals(null, hintFor(Build.VERSION_CODES.O - 1, "Email"))
    }

    @Test fun hint_for_reports_null_when_blank_at_any_level() {
        assertEquals(null, hintFor(Build.VERSION_CODES.O, null))
        assertEquals(null, hintFor(Build.VERSION_CODES.O, ""))
        assertEquals(null, hintFor(Build.VERSION_CODES.O - 1, null))
    }

    @Test fun finds_node_by_preorder_ref() {
        val tree = n("FrameLayout", children = listOf(n("EditText", text = "X"), n("Button")))
        assertEquals("X", nodeByRef(tree, 1)?.text)
        assertEquals(null, nodeByRef(tree, 99))
    }
}
