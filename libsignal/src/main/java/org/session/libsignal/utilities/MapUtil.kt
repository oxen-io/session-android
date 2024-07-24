package org.session.libsignal.utilities

fun <T, K: Any> Iterable<T>.associateByNotNull(
    keySelector: (T) -> K?
) = associateByNotNull(keySelector) { it }

fun <T, K: Any, V: Any> Iterable<T>.associateByNotNull(
    keySelector: (T) -> K?,
    valueTransform: (T) -> V?,
): Map<K, V> = buildMap {
    for (item in this@associateByNotNull) {
        val key = keySelector(item) ?: continue
        val value = valueTransform(item) ?: continue
        this[key] = value
    }
}

fun <T, K: Any, V: Any> Sequence<T>.associateByNotNull(
    keySelector: (T) -> K?,
    valueTransform: (T) -> V?,
): Map<K, V> = asIterable().associateByNotNull(keySelector, valueTransform)

fun <T, K: Any> Sequence<T>.associateByNotNull(
    keySelector: (T) -> K?,
): Map<K, T> = associateByNotNull(keySelector) { it }
