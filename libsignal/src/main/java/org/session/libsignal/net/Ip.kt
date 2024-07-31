package org.session.libsignal.net

@JvmInline
value class Ip(val ip: Long) {
    constructor(ip: String): this(
        ip.takeWhile { it != '/' }.split('.').fold(0L) { acc, octet -> acc shl 8 or octet.toLong() }
    )
    operator fun compareTo(other: Ip) = ip.compareTo(other.ip)
}
