package com.fixedwidth.glassagent

/** One absolute-display point in a pointer path. */
data class Pt(val x: Int, val y: Int, val tMs: Long)

interface Clipboard {
    fun get(): String
    fun set(text: String)
}

interface Input {
    fun pointer(path: List<Pt>, button: String)
    fun key(chord: String)
    fun text(s: String)
}
