package com.fixedwidth.glassa11y

import java.io.BufferedReader
import java.io.Writer

/** The active window's node tree and the package it belongs to; null when there is no active window. */
data class ActiveWindow(val root: NodeData, val pkg: String?)

/** Resolve the active window, or null if none is present. */
fun interface TreeSource { fun tree(pkg: String): ActiveWindow? }

/** Perform an action ("click" | "set_text") on a resolved node, in a window belonging to `pkg`; throw on failure. */
fun interface ActionSink { fun perform(node: NodeData, pkg: String, action: String, text: String?) }

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
                    // `source` ignores `req.pkg`; a null result means no window is active at all,
                    // never that this particular package isn't foreground.
                    val win = source.tree(req.pkg) ?: return Response.error(req.id, "no active window")
                    val resp = Response.okTree(req.id, treeJson(win.root), win.pkg)
                    // Recorded only once the response is actually built, so a throw above (e.g. from
                    // treeJson) can't arm the connection for a tree the caller never received.
                    served = win.pkg
                    servedAny = true
                    resp
                }
                is Request.Action -> {
                    if (!servedAny) {
                        return Response.error(
                            req.id,
                            "no tree has been served on this connection; send a tree request before acting",
                        )
                    }
                    if (served == null) {
                        return Response.error(
                            req.id,
                            "the tree served on this connection came from an unnamed window, so the node's " +
                                "app was never confirmed; re-snapshotting will not fix this",
                        )
                    }
                    val win = source.tree("") ?: return Response.error(req.id, "no active window")
                    // Only the active window's package is checked against `served` below; `req.ref` is
                    // still resolved against this freshly re-read tree, not the one `served` came from.
                    if (win.pkg == null) {
                        return Response.error(
                            req.id,
                            "the active window is currently unnamed, so the node's app cannot be confirmed; " +
                                "this is often transient — re-snapshot once a named window is active",
                        )
                    }
                    if (served != win.pkg) {
                        return Response.error(
                            req.id,
                            "the node came from $served, but ${win.pkg} is now the active window; re-snapshot before acting",
                        )
                    }
                    val node = nodeByRef(win.root, req.ref)
                        ?: return Response.error(req.id, "no node for ref ${req.ref}")
                    sink.perform(node, win.pkg, req.action, req.text)
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
