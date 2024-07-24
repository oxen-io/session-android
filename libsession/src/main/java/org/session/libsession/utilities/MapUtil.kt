package org.session.libsession.utilities

/**
 * An implementation of computeIfAbsent for API 23 and earlier.
 * */
fun <K, V> MutableMap<K, V>.computeIfAbsentV23(key: K, compute: () -> V): V? = this[key]
// At this point if the key is in the Map then it is intentionally null.
    ?: takeUnless { key in this }?.run { compute().also { this[key] = it } }
