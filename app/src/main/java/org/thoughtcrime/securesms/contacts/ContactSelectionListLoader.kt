package org.thoughtcrime.securesms.contacts

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.ContactUtilities
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.util.AsyncLoader

sealed class ContactSelectionListItem {
    class Header(val name: String) : ContactSelectionListItem()
    class Contact(val recipient: Recipient) : ContactSelectionListItem()
}

class ContactSelectionListLoader(context: Context, val mode: Int, val filter: String?) : AsyncLoader<List<ContactSelectionListItem>>(context) {

    object DisplayMode {
        const val FLAG_CONTACTS = 1
        const val FLAG_CLOSED_GROUPS = 1 shl 1
        const val FLAG_OPEN_GROUPS = 1 shl 2
        const val FLAG_ALL = FLAG_CONTACTS or FLAG_CLOSED_GROUPS or FLAG_OPEN_GROUPS
    }

    private fun isFlagSet(flag: Int): Boolean {
        return mode and flag > 0
    }

    override fun loadInBackground(): List<ContactSelectionListItem> {
        val contacts = ContactUtilities.getAllContacts(context).filter {
            if (filter.isNullOrEmpty()) return@filter true
            it.toShortString().contains(filter.trim(), true) || it.address.serialize().contains(filter.trim(), true)
        }.sortedBy {
            it.toShortString()
        }
        val list = mutableListOf<ContactSelectionListItem>()
        if (isFlagSet(DisplayMode.FLAG_CLOSED_GROUPS)) {
            list.addAll(getGroups(contacts))
        }
        if (isFlagSet(DisplayMode.FLAG_OPEN_GROUPS)) {
            list.addAll(getCommunities(contacts))
        }
        if (isFlagSet(DisplayMode.FLAG_CONTACTS)) {
            list.addAll(getContacts(contacts))
        }
        return list
    }

    private fun getContacts(contacts: List<Recipient>): List<ContactSelectionListItem> {
        return getItems(contacts, context.getString(R.string.contactContacts)) {
            !it.isGroupOrCommunityRecipient
        }
    }

    private fun getGroups(contacts: List<Recipient>): List<ContactSelectionListItem> {
        return getItems(contacts, context.getString(R.string.conversationsGroups)) { it.address.isGroup }
    }

    private fun getCommunities(contacts: List<Recipient>): List<ContactSelectionListItem> {
        return getItems(contacts, context.getString(R.string.conversationsCommunities)) {
            it.address.isCommunity
        }
    }

    private fun getItems(contacts: List<Recipient>, title: String, contactFilter: (Recipient) -> Boolean): List<ContactSelectionListItem> {
        val items = contacts.filter(contactFilter).map {
            ContactSelectionListItem.Contact(it)
        }
        if (items.isEmpty()) return listOf()
        val header = ContactSelectionListItem.Header(title)
        return listOf(header) + items
    }
}