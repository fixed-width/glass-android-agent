package com.fixedwidth.glassa11y

import java.io.BufferedReader
import java.io.Writer

/** The active window's node tree and the package it belongs to; null when there is no active window. */
data class ActiveWindow(val root: NodeData, val pkg: String?)

/** Resolve the active window, or null if none is present. */
fun interface TreeSource { fun tree(pkg: String): ActiveWindow? }

/** Perform an action ("click" | "set_text") on a resolved node; throw on failure. */
fun interface ActionSink { fun perform(node: NodeData, action: String, text: String?) }

class Server(private val source: TreeSource, private val sink: ActionSink) {
    /** The package of the window this connection's last `tree` described; null before the first. */
    private var served: String? = null
    private var servedAny = false

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
                    val win = source.tree(req.pkg)
                        ?: return Response.error(req.id, "no window for package ${req.pkg}")
                    served = win.pkg
                    servedAny = true
                    Response.okTree(req.id, treeJson(win.root), win.pkg)
                }
                is Request.Action -> {
                    val win = source.tree("") ?: return Response.error(req.id, "no active window")
                    // A ref names a node in the tree served to this connection; if the window changed
                    // apps, `matchLive` can still find a same-class, same-rectangle node in the new app.
                    if (!servedAny) return Response.error(req.id, "no tree has been served on this connection")
                    if (served == null || win.pkg == null) {
                        return Response.error(req.id, "the window is unnamed, so the node's app cannot be confirmed")
                    }
                    if (served != win.pkg) {
                        return Response.error(
                            req.id,
                            "the node came from $served, but ${win.pkg} is now the active window; re-snapshot before acting",
                        )
                    }
                    val node = nodeByRef(win.root, req.ref)
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
