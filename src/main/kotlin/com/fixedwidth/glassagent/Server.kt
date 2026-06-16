package com.fixedwidth.glassagent

interface Clipboard {
    fun get(): String
    fun set(text: String)
}

interface Input {
    fun pointer(path: List<Pt>, button: String)
    fun key(chord: String)
    fun text(s: String)
}
