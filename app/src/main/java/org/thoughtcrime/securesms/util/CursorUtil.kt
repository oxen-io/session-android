package org.thoughtcrime.securesms.util

import android.database.Cursor

fun Cursor.asSequence(): Sequence<Cursor> = generateSequence { takeIf { moveToNext() } }
fun Cursor.filter(predicate: (Cursor) -> Boolean): Sequence<Cursor> = asSequence().filter(predicate)
fun <T> Cursor.map(transform: (Cursor) -> T): Sequence<T> = asSequence().map(transform)
fun <T> Cursor.mapNotNull(transform: (Cursor) -> T): Sequence<T> = asSequence().mapNotNull(transform)
