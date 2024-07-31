package org.session.libsignal.utilities

/**
 * Returns a [Map] containing the elements from the given Iterable indexed by the key
 * returned from [keySelector] function applied to each element.
 *
 * If any two elements would have the same key returned by [keySelector] the last one gets added to the map.
 *
 * If any element is null or the key is null, it will not be added to the [Map].
 *
 * @see associateBy
 */
fun <T, K> Iterable<T>.associateByNotNull(
    keySelector: (T) -> K?
) = associateByNotNull(keySelector) { it }

/**
 * Returns a [Map] containing the values provided by [valueTransform] and indexed by [keySelector]
 * functions applied to elements of the given collection.
 *
 * If any two elements would have the same key returned by [keySelector] the last one gets added to
 * the map.
 *
 * If any element produces a null key or value it will not be added to the [Map].
 *
 * @see associateBy
 */
fun <E, K, V> Iterable<E>.associateByNotNull(
    keySelector: (E) -> K?,
    valueTransform: (E) -> V?,
): Map<K, V> = mutableMapOf<K, V>().also {
    for(e in this) { it[keySelector(e) ?: continue] = valueTransform(e) ?: continue }
}

fun <T, K, V> Sequence<T>.associateByNotNull(
    keySelector: (T) -> K?,
    valueTransform: (T) -> V?,
): Map<K, V> = asIterable().associateByNotNull(keySelector, valueTransform)

fun <T, K> Sequence<T>.associateByNotNull(
    keySelector: (T) -> K?,
): Map<K, T> = associateByNotNull(keySelector) { it }

/**
 * Groups elements of the original collection by the key returned by the given [keySelector] function
 * applied to each element and returns a map where each group key is associated with a list of
 * corresponding elements, omitting elements with a null key.
 *
 * @see groupBy
 */
inline fun <E, K> Iterable<E>.groupByNotNull(keySelector: (E) -> K?): Map<K, List<E>> = LinkedHashMap<K, MutableList<E>>().also {
    forEach { e -> keySelector(e)?.let { k -> it.getOrPut(k) { mutableListOf() } += e } }
}

/**
 * An implementation of computeIfAbsent for API 23 and earlier.
 * */
fun <K, V> MutableMap<K, V>.computeIfAbsentV23(key: K, compute: () -> V): V? = this[key]
// At this point if the key is in the Map then it is intentionally null.
    ?: takeUnless { key in this }?.run { compute().also { this[key] = it } }
