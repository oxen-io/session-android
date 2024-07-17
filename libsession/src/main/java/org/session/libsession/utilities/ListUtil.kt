package org.session.libsession.utilities

/**
 * @return the last element that satisfies the [predicate].
 *
 * This function has undefined behavior if:
 * - The given [predicate] will return [true] for some number of elements,
 *
 */
inline fun <T> List<T>.binarySearchLast(predicate: (T) -> Boolean): Int {
    var low = 0
    var high = size - 1
    var result: Int = -1

    while (low <= high) {
        val mid = (low + high) / 2

        if (predicate(this[mid])) {
            result = mid
            low = mid + 1
        } else high = mid - 1
    }

    return result
}

inline fun <T> List<T>.binarySearchLastAndGet(predicate: (T) -> Boolean): T? =
    binarySearchLast(predicate).let(this::getOrNull)