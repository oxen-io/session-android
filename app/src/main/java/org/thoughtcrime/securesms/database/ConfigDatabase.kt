package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class ConfigDatabase(context: Context, helper: SQLCipherOpenHelper): Database(context, helper) {

    companion object {
        private const val VARIANT = "variant"
        private const val PUBKEY = "publicKey"
        private const val DATA = "data"
        private const val COMBINED_MESSAGE_HASHES = "combined_message_hashes"

        private const val TABLE_NAME = "configs_table"

        const val CREATE_CONFIG_TABLE_COMMAND =
            "CREATE TABLE $TABLE_NAME ($VARIANT TEXT NOT NULL, $PUBKEY TEXT NOT NULL, $DATA BLOB, $COMBINED_MESSAGE_HASHES TEXT, PRIMARY KEY($VARIANT, $PUBKEY));"
        private const val VARIANT_WHERE = "$VARIANT = ?"
        private const val VARIANT_AND_PUBKEY_WHERE = "$VARIANT = ? AND $PUBKEY = ?"

        const val USER_KEY = "user"
        const val CONTACTS_KEY = "contacts"
        // conversations use publicKey / URL
    }

    fun storeConfig(variant: String, publicKey: String, data: ByteArray) {
        val db = writableDatabase
        val contentValues = contentValuesOf(
            VARIANT to variant,
            PUBKEY to publicKey,
            DATA to data
        )
        db.insertOrUpdate(TABLE_NAME, contentValues, VARIANT_AND_PUBKEY_WHERE, arrayOf(variant, publicKey))
    }

    fun retrieveConfig(variant: String, publicKey: String): ByteArray? {
        val db = readableDatabase
        val query = db.query(TABLE_NAME, arrayOf(DATA), VARIANT_AND_PUBKEY_WHERE, arrayOf(variant, publicKey),null, null, null)
        val bytes = query?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getBlobOrNull(cursor.getColumnIndex(DATA))
        }
        return bytes
    }

}