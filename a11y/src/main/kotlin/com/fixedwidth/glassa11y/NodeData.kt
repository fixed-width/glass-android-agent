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
    val editable: Boolean,
    val clickable: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val children: List<NodeData>,
)

/** Serialize a tree to JSON, assigning a stable pre-order `ref` to each node (root = 0). */
fun treeJson(root: NodeData): String = nodeJson(root, intArrayOf(0)).toString()

private fun nodeJson(n: NodeData, next: IntArray): JSONObject {
    val o = JSONObject()
    o.put("ref", next[0]); next[0] += 1
    o.put("class", n.className)
    n.text?.let { o.put("text", it) }
    n.contentDescription?.let { o.put("desc", it) }
    o.put("bounds", JSONObject().put("x", n.bounds.x).put("y", n.bounds.y)
        .put("w", n.bounds.w).put("h", n.bounds.h))
    o.put("editable", n.editable)
    o.put("clickable", n.clickable)
    o.put("enabled", n.enabled)
    o.put("scrollable", n.scrollable)
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
