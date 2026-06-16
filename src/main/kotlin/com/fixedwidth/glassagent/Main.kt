package com.fixedwidth.glassagent

import android.net.LocalServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.contains("--selftest-clipboard")) {
            val cb = SystemClipboard()
            cb.set("glass-roundtrip-✓-héllo")
            val got = cb.get()
            System.err.println("clipboard selftest: set→get => \"$got\"")
            System.exit(if (got == "glass-roundtrip-✓-héllo") 0 else 1)
        }

        if (args.contains("--clipboard-get")) {
            val cb = SystemClipboard()
            val got = cb.get()
            System.err.println("clipboard-get: \"$got\"")
            return
        }

        val socketName = "glass-agent"
        val server = LocalServerSocket(socketName)
        System.err.println("glass-agent: listening on localabstract:$socketName")
        val client = server.accept()
        val reader = BufferedReader(InputStreamReader(client.inputStream))
        val writer = client.outputStream.bufferedWriter()
        writer.write("{\"hello\":{\"proto\":${Protocol.PROTO}}}\n"); writer.flush()
        // Echo each line until EOF (replaced by the real loop in a later task).
        var line = reader.readLine()
        while (line != null) {
            writer.write("$line\n"); writer.flush()
            line = reader.readLine()
        }
        client.close(); server.close()
    }
}
