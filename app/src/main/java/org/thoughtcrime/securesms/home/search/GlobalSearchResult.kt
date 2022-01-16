package org.thoughtcrime.securesms.home.search

import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.search.model.MessageResult

data class GlobalSearchResult(
    val query: String,
    val contacts: List<Contact>,
    val threads: List<ThreadRecord>,
    val messages: List<MessageResult>
) {
    companion object {

        val EMPTY = GlobalSearchResult("", emptyList(), emptyList(), emptyList())
        const val SEARCH_LIMIT = 5

    }
}
