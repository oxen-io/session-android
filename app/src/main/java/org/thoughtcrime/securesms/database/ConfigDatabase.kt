package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class ConfigDatabase(context: Context, helper: SQLCipherOpenHelper): Database(context, helper) {

    companion object {
        private const val KEY = "key"
        private const val VALUE = "value"

        private const val TABLE_NAME = "configs_table"

        const val CREATE_CONFIG_TABLE_COMMAND = "CREATE TABLE $TABLE_NAME ($KEY TEXT NOT NULL, $VALUE BLOB NOT NULL, PRIMARY KEY($KEY));"
        private const val KEY_WHERE = "$KEY = ?"

        const val USER_KEY = "user"
        const val CONTACTS_KEY = "contacts"
        // conversations use publicKey / URL
    }

    fun storeConfig(key: String, data: ByteArray) {
        val db = writableDatabase
        val contentValues = contentValuesOf(
            KEY to key,
            VALUE to data
        )
        db.insertOrUpdate(TABLE_NAME, contentValues, KEY_WHERE, arrayOf(key))
    }

    fun retrieveConfig(key: String): ByteArray? {
        val db = readableDatabase
        val query = db.query(TABLE_NAME, arrayOf(VALUE), KEY_WHERE, arrayOf(key),null, null, null)
        val bytes = query?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getBlobOrNull(cursor.getColumnIndex(VALUE))
        }
        return bytes
    }

}