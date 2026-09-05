package com.fixedwidth.glassa11y

import org.json.JSONArray
import org.json.JSONObject

/** Screen-space bounds (px). */
data class Bounds(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * A framework-free snapshot of one `AccessibilityNodeInfo`. The service adapts the real
 * node into this so the JSON mapping is unit-testable off-device.
 */
data class NodeData(
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val bounds: Bounds,
    val visible: Boolean,
    val focused: Boolean,
    val focusable: Boolean,
    val password: Boolean,
    val editable: Boolean,
    val clickable: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val resourceId: String?,
    val hint: String?,
    val children: List<NodeData>,
    val showingHintText: Boolean = false,
)

/** Serialize a tree to JSON, assigning a stable pre-order `ref` to each node (root = 0). */
fun treeJson(root: NodeData): String = nodeJson(root, intArrayOf(0)).toString()

private fun nodeJson(n: NodeData, next: IntArray): JSONObject {
    val o = JSONObject()
    o.put("ref", next[0]); next[0] += 1
    o.put("class", n.className)
    // Keep protected text off the wire. Older protocol-1 hosts ignore `password`, so the flag
    // alone cannot stop them from treating the text as an ordinary editable value.
    if (!n.password) n.text?.let { o.put("text", it) }
    n.contentDescription?.let { o.put("desc", it) }
    o.put("bounds", JSONObject().put("x", n.bounds.x).put("y", n.bounds.y)
        .put("w", n.bounds.w).put("h", n.bounds.h))
    o.put("editable", n.editable)
    o.put("visible", n.visible)
    o.put("focused", n.focused)
    o.put("focusable", n.focusable)
    o.put("password", n.password)
    o.put("clickable", n.clickable)
    o.put("enabled", n.enabled)
    o.put("scrollable", n.scrollable)
    o.put("checkable", n.checkable)
    o.put("checked", n.checked)
    n.resourceId?.let { o.put("resource_id", it) }
    n.hint?.let { o.put("hint", it) }
    o.put("showing_hint_text", n.showingHintText)
    if (n.children.isNotEmpty()) {
        val arr = JSONArray()
        n.children.forEach { arr.put(nodeJson(it, next)) }
        o.put("children", arr)
    }
    return o
}

/** Resolve a node by the same pre-order index `treeJson` assigns (root = 0). */
fun nodeByRef(root: NodeData, ref: Int): NodeData? {
    val counter = intArrayOf(0)
    fun walk(n: NodeData): NodeData? {
        if (counter[0] == ref) return n
        counter[0] += 1
        for (c in n.children) walk(c)?.let { return it }
        return null
    }
    return walk(root)
}
