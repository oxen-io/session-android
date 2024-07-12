package org.thoughtcrime.securesms.util

import android.database.Cursor

fun Cursor.asSequence(): Sequence<Cursor> = generateSequence { takeIf { moveToNext() } }
fun <T> Cursor.map(transform: (Cursor) -> T): Sequence<T> = asSequence().map(transform)
