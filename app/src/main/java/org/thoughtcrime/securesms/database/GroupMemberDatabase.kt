package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.session.libsession.messaging.open_groups.GroupMember
import org.session.libsession.messaging.open_groups.GroupMemberRole

class GroupMemberDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        const val TABLE_NAME = "group_member"
        const val ROW_ID = "_id"
        const val GROUP_ID = "group_id"
        const val PROFILE_ID = "profile_id"
        const val ROLE = "role"

        @JvmField
        val CREATE_GROUP_MEMBER_TABLE_COMMAND = """
      CREATE TABLE $TABLE_NAME (
        $ROW_ID INTEGER PRIMARY KEY,
        $GROUP_ID TEXT NOT NULL,
        $PROFILE_ID TEXT NOT NULL,
        $ROLE TEXT NOT NULL
      )
    """.trimIndent()

        private fun readGroupMember(cursor: Cursor): GroupMember {
            return GroupMember(
                groupId = cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)),
                profileId = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_ID)),
                role = GroupMemberRole.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(ROLE))),
            )
        }
    }

    fun getGroupMemberRoles(groupId: String, profileId: String): List<GroupMemberRole> {
        val query = "$GROUP_ID = ? AND $PROFILE_ID = ?"
        val args = arrayOf(groupId, profileId)

        val mappings: MutableList<GroupMember> = mutableListOf()

        readableDatabase.query(TABLE_NAME, null, query, args, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                mappings += readGroupMember(cursor)
            }
        }

        return mappings.map { it.role }
    }

    fun addGroupMember(member: GroupMember) {
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(GROUP_ID, member.groupId)
                put(PROFILE_ID, member.profileId)
                put(ROLE, member.role.name)
            }

            writableDatabase.insert(TABLE_NAME, null, values)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

}