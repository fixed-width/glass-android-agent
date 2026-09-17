package com.fixedwidth.glassa11y

import org.json.JSONObject

sealed class Request {
    abstract val id: Int
    data class Ping(override val id: Int) : Request()
    data class Tree(override val id: Int, val pkg: String, val point: ScreenPoint? = null) : Request()
    data class Action(override val id: Int, val ref: Int, val action: String, val text: String?) : Request()
    data class Unknown(override val id: Int, val op: String) : Request()
    data class Malformed(val raw: String) : Request() { override val id = -1 }
}

data class Response(
    val id: Int, val ok: Boolean,
    val treeJson: String? = null, val error: String? = null, val pkg: String? = null,
    val pointerWindow: PointerWindow? = null,
) {
    companion object {
        fun ok(id: Int) = Response(id, true)
        fun okTree(id: Int, treeJson: String, pkg: String?) = Response(id, true, treeJson = treeJson, pkg = pkg)
        fun error(id: Int, msg: String) = Response(id, false, error = msg)
    }
}

object Protocol {
    const val PROTO = 1
    const val NODE_SCHEMA = 2

    fun helloLine(): String = JSONObject().put(
        "hello",
        JSONObject().put("proto", PROTO).put("node_schema", NODE_SCHEMA),
    ).toString()

    fun parse(line: String): Request {
        return try {
            val o = JSONObject(line)
            val id = o.optInt("id", -1)
            if (id < 0) return Request.Malformed(line)
            when (val op = o.optString("op")) {
                "ping" -> Request.Ping(id)
                "tree" -> {
                    val point = if (o.has("pointer_point")) {
                        val p = o.getJSONObject("pointer_point")
                        fun coordinate(key: String): Int {
                            val value = p.get(key)
                            require(value is Int || value is Long) { "non-integer coordinate" }
                            val number = (value as Number).toLong()
                            require(number in Int.MIN_VALUE..Int.MAX_VALUE) { "coordinate overflow" }
                            return number.toInt()
                        }
                        ScreenPoint(coordinate("x"), coordinate("y"))
                    } else null
                    Request.Tree(id, o.optString("package"), point)
                }
                "action" -> Request.Action(
                    id, o.optInt("ref", -1), o.optString("action"),
                    if (o.has("text")) o.optString("text") else null,
                )
                else -> Request.Unknown(id, op)
            }
        } catch (e: Exception) {
            Request.Malformed(line)
        }
    }

    fun serialize(r: Response): String {
        val o = JSONObject().put("id", r.id).put("ok", r.ok)
        // `tree` is a pre-serialized JSON object; nest it as a value, not a string.
        if (r.treeJson != null) o.put("tree", JSONObject(r.treeJson))
        // Absent rather than null when the platform did not name the window: an older host ignores the
        // key, and a newer one reads absent as "cannot say" rather than as a mismatch.
        if (r.pkg != null) o.put("package", r.pkg)
        r.pointerWindow?.let { evidence ->
            val pointer = JSONObject().put("version", 1)
                .put("x", evidence.point.x).put("y", evidence.point.y)
                .put("window_id", evidence.windowId).put("display_id", evidence.displayId)
            evidence.occludingWindowId?.let { pointer.put("occluding_window_id", it) }
            o.put("pointer_window", pointer)
        }
        if (r.error != null) o.put("error", r.error)
        return o.toString()
    }
}
