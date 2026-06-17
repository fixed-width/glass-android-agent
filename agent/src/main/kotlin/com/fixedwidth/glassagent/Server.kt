package com.fixedwidth.glassagent

import java.io.BufferedReader
import java.io.Writer

interface Clipboard {
    fun get(): String
    fun set(text: String)
}

interface Input {
    fun pointer(path: List<Pt>, button: String)
    fun key(chord: String)
    fun text(s: String)
    fun gesture(paths: List<List<Pt>>)
}

class Server(private val clipboard: Clipboard, private val input: Input) {
    fun serve(reader: BufferedReader, writer: Writer) {
        writer.write(Protocol.helloLine() + "\n"); writer.flush()
        var line = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                val resp = handle(Protocol.parse(line))
                writer.write(Protocol.serialize(resp) + "\n"); writer.flush()
            }
            line = reader.readLine()
        }
    }

    fun handle(req: Request): Response = try {
        when (req) {
            is Request.Ping -> Response.ok(req.id)
            is Request.ClipboardGet -> Response.okText(req.id, clipboard.get())
            is Request.ClipboardSet -> { clipboard.set(req.text); Response.ok(req.id) }
            is Request.Pointer -> { input.pointer(req.path, req.button); Response.ok(req.id) }
            is Request.Gesture -> { input.gesture(req.paths); Response.ok(req.id) }
            is Request.Key -> { input.key(req.chord); Response.ok(req.id) }
            is Request.Text -> { input.text(req.text); Response.ok(req.id) }
            is Request.Unknown -> Response.error(req.id, "unknown op: ${req.op}")
            is Request.Malformed -> Response.error(req.id, "malformed request")
        }
    } catch (e: Exception) {
        Response.error(req.id, e.message ?: e.javaClass.simpleName)
    }
}
