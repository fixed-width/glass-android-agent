package com.fixedwidth.glassa11y

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** Adapt a live node subtree into the framework-free [NodeData] model (screen bounds). */
fun adapt(node: AccessibilityNodeInfo?): NodeData? {
    if (node == null) return null
    val r = Rect().also { node.getBoundsInScreen(it) }
    val kids = ArrayList<NodeData>(node.childCount)
    for (i in 0 until node.childCount) adapt(node.getChild(i))?.let { kids.add(it) }
    return NodeData(
        className = node.className?.toString() ?: "",
        text = node.text?.toString()?.ifEmpty { null },
        contentDescription = node.contentDescription?.toString()?.ifEmpty { null },
        bounds = Bounds(r.left, r.top, r.width(), r.height()),
        editable = node.isEditable,
        clickable = node.isClickable,
        enabled = node.isEnabled,
        scrollable = node.isScrollable,
        children = kids,
    )
}

/** Perform a node action ("click" | "set_text") on a live node; throws on refusal/unknown. */
fun performOn(node: AccessibilityNodeInfo, action: String, text: String?) {
    when (action) {
        "click" -> require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "ACTION_CLICK refused" }
        "set_text" -> {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text ?: "")
            }
            require(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { "ACTION_SET_TEXT refused" }
        }
        else -> throw IllegalArgumentException("unknown action: $action")
    }
}
