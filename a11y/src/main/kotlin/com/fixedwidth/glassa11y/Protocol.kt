package com.fixedwidth.glassa11y

import org.json.JSONObject

sealed class Request {
    abstract val id: Int
    data class Ping(override val id: Int) : Request()
    data class Tree(override val id: Int, val pkg: String) : Request()
    data class Action(override val id: Int, val ref: Int, val action: String, val text: String?) : Request()
    data class Unknown(override val id: Int, val op: String) : Request()
    data class Malformed(val raw: String) : Request() { override val id = -1 }
}

data class Response(
    val id: Int, val ok: Boolean,
    val treeJson: String? = null, val error: String? = null,
) {
    companion object {
        fun ok(id: Int) = Response(id, true)
        fun okTree(id: Int, treeJson: String) = Response(id, true, treeJson = treeJson)
        fun error(id: Int, msg: String) = Response(id, false, error = msg)
    }
}

object Protocol {
    const val PROTO = 1

    fun helloLine(): String = JSONObject().put("hello", JSONObject().put("proto", PROTO)).toString()

    fun parse(line: String): Request {
        return try {
            val o = JSONObject(line)
            val id = o.optInt("id", -1)
            if (id < 0) return Request.Malformed(line)
            when (val op = o.optString("op")) {
                "ping" -> Request.Ping(id)
                "tree" -> Request.Tree(id, o.optString("package"))
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
        if (r.error != null) o.put("error", r.error)
        return o.toString()
    }
}
