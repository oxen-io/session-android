package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getLongOrNull
import androidx.sqlite.db.transaction
import org.session.libsignal.protos.SignalServiceProtos.SharedConfigMessage
import org.session.libsignal.utilities.SessionId
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
        private const val VARIANT_IN_AND_PUBKEY_WHERE = "$VARIANT in (?) AND $PUBKEY = ?"

        val KEYS_VARIANT = SharedConfigMessage.Kind.ENCRYPTION_KEYS.name
        val INFO_VARIANT = SharedConfigMessage.Kind.CLOSED_GROUP_INFO.name
        val MEMBER_VARIANT = SharedConfigMessage.Kind.CLOSED_GROUP_MEMBERS.name
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

    fun deleteGroupConfigs(closedGroupId: SessionId) {
        val db = writableDatabase
        db.transaction {
            val variants = arrayOf(KEYS_VARIANT, INFO_VARIANT, MEMBER_VARIANT)
            db.delete(TABLE_NAME, VARIANT_IN_AND_PUBKEY_WHERE,
                arrayOf(variants, closedGroupId.hexString())
            )
        }
    }

    fun storeGroupConfigs(publicKey: String, keysConfig: ByteArray, infoConfig: ByteArray, memberConfig: ByteArray, timestamp: Long) {
        val db = writableDatabase
        db.transaction {
            val keyContent = contentValuesOf(
                VARIANT to KEYS_VARIANT,
                PUBKEY to publicKey,
                DATA to keysConfig,
                TIMESTAMP to timestamp
            )
            db.insertOrUpdate(TABLE_NAME, keyContent, VARIANT_AND_PUBKEY_WHERE,
                arrayOf(KEYS_VARIANT, publicKey)
            )
            val infoContent = contentValuesOf(
                VARIANT to INFO_VARIANT,
                PUBKEY to publicKey,
                DATA to infoConfig,
                TIMESTAMP to timestamp
            )
            db.insertOrUpdate(TABLE_NAME, infoContent, VARIANT_AND_PUBKEY_WHERE,
                arrayOf(INFO_VARIANT, publicKey)
            )
            val memberContent = contentValuesOf(
                VARIANT to MEMBER_VARIANT,
                PUBKEY to publicKey,
                DATA to memberConfig,
                TIMESTAMP to timestamp
            )
            db.insertOrUpdate(TABLE_NAME, memberContent, VARIANT_AND_PUBKEY_WHERE,
                arrayOf(MEMBER_VARIANT, publicKey)
            )
        }
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