package org.session.libsession.utilities

/**
 * Find the index of the last element that matches the given predicate
 * assuming that the predicate returns true for all elements at or before the index, and false for
 * all elements after.
 *
 * @return the index of the last element that matches the predicate, or -1 if no such element is found.
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

/**
 * Find the last element that matches the given predicate
 * assuming that the predicate returns true for the target element and all prior elements, and false for
 * all elements after the target element.
 *
 * @return the last element that matches the predicate, or null if no such element is found.
 */
inline fun <T> List<T>.binarySearchLastAndGet(predicate: (T) -> Boolean): T? =
    binarySearchLast(predicate).let(this::getOrNull)
