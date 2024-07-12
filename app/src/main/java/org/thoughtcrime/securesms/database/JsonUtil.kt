package org.thoughtcrime.securesms.database

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.forEach(action: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        action(getJSONObject(i))
    }
}

fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = buildList {
    for (i in 0 until length()) {
        add(transform(getJSONObject(i)))
    }
}

fun JSONArray.toList(): List<JSONObject> = buildList {
    for (i in 0 until length()) {
        add(getJSONObject(i))
    }
}
