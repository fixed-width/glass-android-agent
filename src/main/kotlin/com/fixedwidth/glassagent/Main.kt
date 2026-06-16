package com.fixedwidth.glassagent

import android.net.LocalServerSocket

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.contains("--selftest-clipboard")) { selftestClipboard(); return }

        val server = LocalServerSocket("glass-agent")
        System.err.println("glass-agent: listening on localabstract:glass-agent")
        val clipboard = SystemClipboard()
        val input = SystemInput()
        while (true) {
            val client = try { server.accept() } catch (e: Exception) { break }
            try {
                Server(clipboard, input).serve(
                    client.inputStream.bufferedReader(),
                    client.outputStream.bufferedWriter()
                )
            } catch (e: Exception) {
                System.err.println("glass-agent: connection error: ${e.message}")
            } finally {
                runCatching { client.close() }
            }
        }
        server.close()
    }

    private fun selftestClipboard() {
        val cb = SystemClipboard()
        cb.set("glass-roundtrip-✓-héllo")
        val got = cb.get()
        System.err.println("clipboard selftest: set→get => \"$got\"")
        System.exit(if (got == "glass-roundtrip-✓-héllo") 0 else 1)
    }
}
