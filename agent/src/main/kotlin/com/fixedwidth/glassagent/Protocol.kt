package com.fixedwidth.glassagent

import org.json.JSONArray
import org.json.JSONObject

/** One absolute-display point in a pointer path. */
data class Pt(val x: Int, val y: Int, val tMs: Long)

sealed class Request {
    abstract val id: Int
    data class Ping(override val id: Int) : Request()
    data class ClipboardGet(override val id: Int) : Request()
    data class ClipboardSet(override val id: Int, val text: String) : Request()
    data class Pointer(override val id: Int, val path: List<Pt>, val button: String) : Request()
    data class Key(override val id: Int, val chord: String) : Request()
    data class Text(override val id: Int, val text: String) : Request()
    data class Unknown(override val id: Int, val op: String) : Request()
    /**
     * A line that isn't valid JSON, has no usable id, or has a malformed field. `id = -1`
     * is a synthetic sentinel — the server should not send a normal id-keyed response for it.
     */
    data class Malformed(val raw: String) : Request() { override val id = -1 }
}

data class Response(val id: Int, val ok: Boolean, val text: String? = null, val error: String? = null) {
    companion object {
        fun ok(id: Int) = Response(id, true)
        fun okText(id: Int, text: String) = Response(id, true, text = text)
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
            // optString returns "" when "op" is absent; that falls through to Unknown(id, "").
            when (val op = o.optString("op")) {
                "ping" -> Request.Ping(id)
                "clipboard_get" -> Request.ClipboardGet(id)
                "clipboard_set" -> Request.ClipboardSet(id, o.optString("text"))
                "key" -> Request.Key(id, o.optString("chord"))
                "text" -> Request.Text(id, o.optString("text"))
                "pointer" -> {
                    val arr = o.optJSONArray("gesture") ?: JSONArray()
                    val path = (0 until arr.length()).map { i ->
                        val p = arr.getJSONObject(i)
                        Pt(p.getInt("x"), p.getInt("y"), p.optLong("t_ms", 0))
                    }
                    Request.Pointer(id, path, o.optString("button", "left"))
                }
                else -> Request.Unknown(id, op)
            }
        } catch (e: Exception) {
            // Any malformed field (e.g. a pointer point missing x/y) degrades to Malformed
            // rather than throwing into the connection loop.
            Request.Malformed(line)
        }
    }

    fun serialize(r: Response): String {
        val o = JSONObject().put("id", r.id).put("ok", r.ok)
        if (r.text != null) o.put("text", r.text)
        if (r.error != null) o.put("error", r.error)
        return o.toString()
    }
}
