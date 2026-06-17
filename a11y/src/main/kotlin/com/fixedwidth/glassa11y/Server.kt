package com.fixedwidth.glassa11y

import java.io.BufferedReader
import java.io.Writer

/** Resolve the active-window node tree for a package, or null if no such window is present. */
fun interface TreeSource { fun tree(pkg: String): NodeData? }

/** Perform an action ("click" | "set_text") on a resolved node; throw on failure. */
fun interface ActionSink { fun perform(node: NodeData, action: String, text: String?) }

class Server(private val source: TreeSource, private val sink: ActionSink) {
    fun serve(reader: BufferedReader, writer: Writer) {
        writer.write(Protocol.helloLine() + "\n"); writer.flush()
        var line = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                writer.write(Protocol.serialize(handle(Protocol.parse(line))) + "\n"); writer.flush()
            }
            line = reader.readLine()
        }
    }

    fun handle(req: Request): Response {
        return try {
            when (req) {
                is Request.Ping -> Response.ok(req.id)
                is Request.Tree -> {
                    val root = source.tree(req.pkg)
                        ?: return Response.error(req.id, "no window for package ${req.pkg}")
                    Response.okTree(req.id, treeJson(root))
                }
                is Request.Action -> {
                    val root = source.tree("") // package-agnostic re-walk; null when no active window
                        ?: return Response.error(req.id, "no active window")
                    val node = nodeByRef(root, req.ref)
                        ?: return Response.error(req.id, "no node for ref ${req.ref}")
                    sink.perform(node, req.action, req.text)
                    Response.ok(req.id)
                }
                is Request.Unknown -> Response.error(req.id, "unknown op: ${req.op}")
                is Request.Malformed -> Response.error(req.id, "malformed request")
            }
        } catch (e: Exception) {
            Response.error(req.id, e.message ?: e.javaClass.simpleName)
        }
    }
}
