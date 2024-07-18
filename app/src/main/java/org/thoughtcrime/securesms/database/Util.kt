package org.thoughtcrime.securesms.database

import android.content.Context
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

fun Context.threadDatabase() = DatabaseComponent.get(this).threadDatabase()