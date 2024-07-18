package org.session.libsignal.utilities

private val HEX_CHARS = buildSet {
    addAll('0'..'9')
    addAll('a'..'f')
    addAll('A'..'F')
}

fun String.isHex() = all { it in HEX_CHARS }
