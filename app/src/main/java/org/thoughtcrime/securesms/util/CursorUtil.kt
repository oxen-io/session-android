package org.thoughtcrime.securesms.util

import android.database.Cursor

fun Cursor.asSequence(): Sequence<Cursor> =
    generateSequence { if (moveToNext()) this else null }
