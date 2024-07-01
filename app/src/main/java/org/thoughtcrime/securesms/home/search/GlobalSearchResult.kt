package org.thoughtcrime.securesms.home.search

import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.GroupRecord
import org.thoughtcrime.securesms.search.model.MessageResult
import org.thoughtcrime.securesms.search.model.SearchResult

data class GlobalSearchResult(
    val query: String,
    val contacts: List<Contact> = emptyList(),
    val threads: List<GroupRecord> = emptyList(),
    val messages: List<MessageResult> = emptyList()
) {
    val isEmpty: Boolean
        get() = contacts.isEmpty() && threads.isEmpty() && messages.isEmpty()

    companion object {
        val EMPTY = GlobalSearchResult("")
    }
}

fun SearchResult.toGlobalSearchResult(): GlobalSearchResult = try {
    GlobalSearchResult(query, contacts.toList(), conversations.toList(), messages.toList())
} finally {
    close()
}
