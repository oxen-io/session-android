package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getLongOrNull
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class ConfigDatabase(context: Context, helper: SQLCipherOpenHelper): Database(context, helper) {

    companion object {
        private const val VARIANT = "variant"
        private const val PUBKEY = "publicKey"
        private const val DATA = "data"
        private const val TIMESTAMP = "timestamp"   // Milliseconds

        private const val TABLE_NAME = "configs_table"

        const val CREATE_CONFIG_TABLE_COMMAND =
            "CREATE TABLE $TABLE_NAME ($VARIANT TEXT NOT NULL, $PUBKEY TEXT NOT NULL, $DATA BLOB, $TIMESTAMP INTEGER NOT NULL DEFAULT 0, PRIMARY KEY($VARIANT, $PUBKEY));"

        private const val VARIANT_AND_PUBKEY_WHERE = "$VARIANT = ? AND $PUBKEY = ?"
    }

    fun storeConfig(variant: String, publicKey: String, data: ByteArray, timestamp: Long) {
        val db = writableDatabase
        val contentValues = contentValuesOf(
            VARIANT to variant,
            PUBKEY to publicKey,
            DATA to data,
            TIMESTAMP to timestamp
        )
        db.insertOrUpdate(TABLE_NAME, contentValues, VARIANT_AND_PUBKEY_WHERE, arrayOf(variant, publicKey))
    }

    fun retrieveConfigAndHashes(variant: String, publicKey: String): ByteArray? {
        val db = readableDatabase
        val query = db.query(TABLE_NAME, arrayOf(DATA), VARIANT_AND_PUBKEY_WHERE, arrayOf(variant, publicKey),null, null, null)
        return query?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val bytes = cursor.getBlobOrNull(cursor.getColumnIndex(DATA)) ?: return@use null
            bytes
        }
    }

    fun retrieveConfigLastUpdateTimestamp(variant: String, publicKey: String): Long {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, arrayOf(TIMESTAMP), VARIANT_AND_PUBKEY_WHERE, arrayOf(variant, publicKey),null, null, null)
        if (cursor == null) return 0
        if (!cursor.moveToFirst()) return 0
        return (cursor.getLongOrNull(cursor.getColumnIndex(TIMESTAMP)) ?: 0)
    }
}