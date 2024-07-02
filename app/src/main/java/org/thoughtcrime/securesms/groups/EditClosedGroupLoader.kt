package org.thoughtcrime.securesms.groups

import android.content.Context
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.AsyncLoader

class EditClosedGroupLoader(context: Context, val groupID: String) : AsyncLoader<EditLegacyClosedGroupActivity.GroupMembers>(context) {

    override fun loadInBackground(): EditLegacyClosedGroupActivity.GroupMembers {
        val groupDatabase = DatabaseComponent.get(context).groupDatabase()
        val members = groupDatabase.getGroupMembers(groupID, true)
        val zombieMembers = groupDatabase.getGroupZombieMembers(groupID)
        return EditLegacyClosedGroupActivity.GroupMembers(
                members.map {
                    it.address.toString()
                },
                zombieMembers.map {
                    it.address.toString()
                }
        )
    }
}