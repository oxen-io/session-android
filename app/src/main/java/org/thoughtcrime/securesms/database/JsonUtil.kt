package org.thoughtcrime.securesms.database

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.asObjectSequence(): Sequence<JSONObject> = (0 until length()).asSequence().map(::getJSONObject)

fun <T> JSONArray.map(transform: JSONArray.(Int) -> T): Sequence<T> = (0 until length()).asSequence().map { transform(it) }
