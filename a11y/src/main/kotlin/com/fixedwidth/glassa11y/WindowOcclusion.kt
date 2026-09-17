package com.fixedwidth.glassa11y

/** Screen pixels, matching Android input coordinates. */
data class ScreenPoint(val x: Int, val y: Int)

data class PointerWindow(
    val point: ScreenPoint,
    val windowId: Int,
    val displayId: Int,
    val occludingWindowId: Int?,
)

/** Facts from one window-list read at the requested point. */
data class WindowAtPoint(
    val id: Int,
    val displayId: Int,
    val layer: Int,
    val containsPoint: Boolean,
    val eligibleCover: Boolean,
)

/** A covering window is negative evidence only; its absence does not prove a control clear. */
fun coveringWindow(targetId: Int, windows: List<WindowAtPoint>): Int? {
    if (targetId < 0 || windows.any { it.id < 0 } || windows.map { it.id }.distinct().size != windows.size) return null
    val target = windows.singleOrNull { it.id == targetId } ?: return null
    if (target.displayId != 0 || !target.containsPoint) return null
    return windows.filter {
        it.displayId == target.displayId && it.layer > target.layer &&
            it.containsPoint && it.eligibleCover
    }.maxByOrNull { it.layer }?.id
}
