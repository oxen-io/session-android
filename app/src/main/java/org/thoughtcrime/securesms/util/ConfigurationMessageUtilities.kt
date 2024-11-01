package org.thoughtcrime.securesms.util

import org.session.libsession.utilities.GroupUtil
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase

object ConfigurationMessageUtilities {
    @JvmField
    val DELETE_INACTIVE_GROUPS: String = """
        DELETE FROM ${GroupDatabase.TABLE_NAME} WHERE ${GroupDatabase.GROUP_ID} IN (SELECT ${ThreadDatabase.ADDRESS} FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.MESSAGE_COUNT} <= 0 AND ${ThreadDatabase.ADDRESS} LIKE '${GroupUtil.LEGACY_CLOSED_GROUP_PREFIX}%');
        DELETE FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.ADDRESS} IN (SELECT ${ThreadDatabase.ADDRESS} FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.MESSAGE_COUNT} <= 0 AND ${ThreadDatabase.ADDRESS} LIKE '${GroupUtil.LEGACY_CLOSED_GROUP_PREFIX}%');
    """.trimIndent()

    @JvmField
    val DELETE_INACTIVE_ONE_TO_ONES: String = """
        DELETE FROM ${ThreadDatabase.TABLE_NAME} WHERE ${ThreadDatabase.MESSAGE_COUNT} <= 0 AND ${ThreadDatabase.ADDRESS} NOT LIKE '${GroupUtil.LEGACY_CLOSED_GROUP_PREFIX}%' AND ${ThreadDatabase.ADDRESS} NOT LIKE '${GroupUtil.COMMUNITY_PREFIX}%' AND ${ThreadDatabase.ADDRESS} NOT LIKE '${GroupUtil.COMMUNITY_INBOX_PREFIX}%';
    """.trimIndent()

}