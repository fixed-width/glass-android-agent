package com.fixedwidth.glassagent

import java.io.StringReader
import java.io.StringWriter
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeClipboard(var value: String = "") : Clipboard {
    override fun get() = value
    override fun set(text: String) { value = text }
}
private class RecordingInput : Input {
    val calls = mutableListOf<String>()
    override fun pointer(path: List<Pt>, button: String) { calls += "pointer:${path.size}:$button" }
    override fun key(chord: String) { calls += "key:$chord" }
    override fun text(s: String) { calls += "text:$s" }
}

class ServerTest {
    private fun run(input: String, cb: Clipboard = FakeClipboard(), inp: Input = RecordingInput()): String {
        val out = StringWriter()
        Server(cb, inp).serve(StringReader(input).buffered(), out)
        return out.toString()
    }

    @Test fun emits_hello_first() {
        val out = run("")
        assertTrue(out.lineSequence().first() == Protocol.helloLine())
    }
    @Test fun ping_responds_ok() {
        val out = run("""{"id":1,"op":"ping"}""" + "\n")
        assertTrue(out.contains("""{"id":1,"ok":true}"""))
    }
    @Test fun clipboard_roundtrip() {
        val cb = FakeClipboard()
        val out = run("""{"id":1,"op":"clipboard_set","text":"hey"}""" + "\n" + """{"id":2,"op":"clipboard_get"}""" + "\n", cb)
        assertEquals("hey", cb.value)
        val getResp = out.trim().lines().last { it.contains(""""id":2""") }
        val o = JSONObject(getResp)
        assertTrue(o.getBoolean("ok")); assertEquals("hey", o.getString("text"))
    }
    @Test fun input_ops_are_dispatched() {
        val inp = RecordingInput()
        run(
            """{"id":1,"op":"key","chord":"ctrl+a"}""" + "\n" +
            """{"id":2,"op":"text","text":"hi"}""" + "\n" +
            """{"id":3,"op":"pointer","gesture":[{"x":5,"y":10,"t_ms":0}],"button":"left"}""" + "\n",
            inp = inp
        )
        assertEquals(listOf("key:ctrl+a", "text:hi", "pointer:1:left"), inp.calls)
    }
    @Test fun unknown_op_returns_error() {
        val out = run("""{"id":9,"op":"frobnicate"}""" + "\n")
        assertTrue(out.contains(""""id":9,"ok":false"""))
    }
    @Test fun handler_exception_becomes_error_response() {
        val throwing = object : Clipboard {
            override fun get(): String = throw RuntimeException("boom")
            override fun set(text: String) {}
        }
        val out = run("""{"id":5,"op":"clipboard_get"}""" + "\n", cb = throwing)
        assertTrue(out.contains(""""id":5,"ok":false""") && out.contains("boom"))
    }
}
