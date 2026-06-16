package com.fixedwidth.glassagent

import android.content.ClipData
import android.os.IBinder

class SystemClipboard : Clipboard {
    private val iface: Any
    private val pkg = "com.android.shell"

    init {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java)
            .invoke(null, "clipboard") as IBinder?
            ?: error("clipboard binder not available — is the process running as the shell uid?")
        val stub = Class.forName("android.content.IClipboard\$Stub")
        iface = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    // Fill an argument of the given parameter type. The FIRST String param is the calling
    // package; a SECOND String param is the attributionTag (pass null); ints are user/device id 0.
    private fun buildArgs(method: java.lang.reflect.Method, clip: ClipData?): Array<Any?> {
        var stringSeen = 0
        return method.parameterTypes.map { type ->
            when {
                type == ClipData::class.java -> clip
                type == String::class.java -> if (stringSeen++ == 0) pkg else null
                type == Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }.toTypedArray()
    }

    override fun get(): String {
        // API 34: exactly one getPrimaryClip overload on IClipboard$Stub$Proxy; if an OEM ROM
        // adds overloads, discriminate by parameterTypes here.
        val m = iface.javaClass.methods.first { it.name == "getPrimaryClip" }
        val clip = m.invoke(iface, *buildArgs(m, null)) as ClipData? ?: return ""
        // null Context is fine for plain-text items (all this agent sets); a non-text item
        // written by another app falls back to its string form, which is acceptable here.
        return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(null)?.toString() ?: "" else ""
    }

    override fun set(text: String) {
        val clip = ClipData.newPlainText("glass", text)
        // API 34: exactly one setPrimaryClip overload on IClipboard$Stub$Proxy; if an OEM ROM
        // adds overloads, discriminate by parameterTypes here.
        val m = iface.javaClass.methods.first { it.name == "setPrimaryClip" }
        m.invoke(iface, *buildArgs(m, clip))
    }
}
