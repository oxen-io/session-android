package org.thoughtcrime.securesms.groups

import androidx.annotation.VisibleForTesting
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

object OpenGroupMigrator {

    const val LEGACY_GROUP_ENCODED_ID = "__loki_public_chat_group__!687474703a2f2f3131362e3230332e37302e33332e" // old IP based toByteArray()
    const val NEW_GROUP_ENCODED_ID = "__loki_public_chat_group__!68747470733a2f2f6f70656e2e67657473657373696f6e2e6f72672e" // new URL based toByteArray()

    @VisibleForTesting
    fun Recipient.roomStub(): String? {
        if (!isOpenGroupRecipient) return null
        val serialized = address.serialize()
        if (serialized.startsWith(LEGACY_GROUP_ENCODED_ID)) {
            return serialized.replace(LEGACY_GROUP_ENCODED_ID,"")
        } else if (serialized.startsWith(NEW_GROUP_ENCODED_ID)) {
            return serialized.replace(NEW_GROUP_ENCODED_ID,"")
        }
        return null
    }

    @VisibleForTesting
    fun getExistingMappings(legacy: List<ThreadRecord>, new: List<ThreadRecord>): List<Pair<Long,Long?>> {
        val legacyStubsMapping = legacy.mapNotNull { thread ->
            val stub = thread.recipient.roomStub()
            stub?.let { it to thread.threadId }
        }
        val newStubsMapping = new.mapNotNull { thread ->
            val stub = thread.recipient.roomStub()
            stub?.let { it to thread.threadId }
        }
        return legacyStubsMapping.map { (legacyStub, legacyId) ->
            // get 'new' open group thread ID if stubs match
            legacyId to newStubsMapping.firstOrNull { (newStub, _) -> newStub == legacyStub }?.second
        }
    }

    @JvmStatic
    fun migrate(databaseComponent: DatabaseComponent) {
        // migrate thread db
        val threadDb = databaseComponent.threadDatabase()
        val legacyOpenGroups = threadDb.legacyOxenOpenGroupIds
        if (legacyOpenGroups.isEmpty()) return // no need to migrate

        val newOpenGroups = threadDb.newOxenOpenGroupIds
        val mappings = getExistingMappings(legacyOpenGroups, newOpenGroups)
        Log.d("Loki-Migration", "legacyOpenGroups size: ${legacyOpenGroups.size}")
        Log.d("Loki-Migration", "newOpenGroups size: ${newOpenGroups.size}")
        Log.d("Loki-Migration", "mappings are: $mappings")
    }

}