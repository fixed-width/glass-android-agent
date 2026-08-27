package com.fixedwidth.glassa11y

import android.graphics.Rect
import android.os.Build
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
        clickable = isClickableNode(node.isClickable, node.actionList.map { it.id }),
        enabled = node.isEnabled,
        scrollable = node.isScrollable,
        checkable = node.isCheckable,
        checked = node.isChecked,
        resourceId = node.viewIdResourceName?.ifEmpty { null },
        // getHintText() is API 26; guarded so it's never called below minSdk 24's floor.
        hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hintFor(Build.VERSION.SDK_INT, node.hintText)
        } else {
            null
        },
        children = kids,
        // isShowingHintText() is API 26; false below that floor means the host keeps the
        // legacy value mapping rather than suppressing text without authoritative evidence.
        showingHintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showingHintTextFor(Build.VERSION.SDK_INT, node.isShowingHintText)
        } else {
            false
        },
    )
}

/**
 * Whether to report a node as clickable: its `isClickable` flag, OR an `ACTION_CLICK` in its
 * action list. Jetpack Compose exposes a button's click via the action, not the flag — so
 * `isClickable` is false for Compose buttons even though they are clickable.
 */
fun isClickableNode(isClickable: Boolean, actionIds: List<Int>): Boolean =
    isClickable || actionIds.contains(AccessibilityNodeInfo.ACTION_CLICK)

/**
 * The hint to report: `null` below API 26 (`getHintText()`'s floor) or when blank, the raw
 * text otherwise. Pure so the version boundary is unit-testable without a live node.
 */
fun hintFor(sdkInt: Int, raw: CharSequence?): String? =
    if (sdkInt >= Build.VERSION_CODES.O) raw?.toString()?.ifEmpty { null } else null

/** Whether node text is the currently displayed hint; the platform fact exists from API 26. */
fun showingHintTextFor(sdkInt: Int, raw: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.O && raw

/**
 * Refuse to actuate if `actual` (the package of the window just read, live) differs from `expected`
 * (the package the ref was gated for). Pure so the check itself is unit-testable off-device — the
 * live read it guards is not.
 */
fun requireLivePackage(expected: String, actual: String?) {
    check(actual == expected) { "the active window is now $actual, not $expected; refusing to act" }
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
