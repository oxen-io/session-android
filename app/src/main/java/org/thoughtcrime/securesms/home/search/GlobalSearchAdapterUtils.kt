package org.thoughtcrime.securesms.home.search

import android.graphics.Typeface
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.ContentView
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Conversation
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Message
import org.thoughtcrime.securesms.util.SearchUtil
import java.util.Locale
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Contact as ContactModel


class GlobalSearchDiff(
    private val oldQuery: String?,
    private val newQuery: String?,
    private val oldData: List<GlobalSearchAdapter.Model>,
    private val newData: List<GlobalSearchAdapter.Model>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldData.size
    override fun getNewListSize(): Int = newData.size
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldData[oldItemPosition] == newData[newItemPosition]

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldQuery == newQuery && oldData[oldItemPosition] == newData[newItemPosition]

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
        if (oldQuery != newQuery) newQuery
        else null
}

private val BoldStyleFactory = { StyleSpan(Typeface.BOLD) }

fun ContentView.bindQuery(query: String, model: GlobalSearchAdapter.Model) {
    when (model) {
        is ContactModel -> bindHighlight(
            query,
            model.contact.getSearchName(),
            binding.searchResultTitle
        )
        is Message -> bindHighlight(
            query,
            model.messageResult.bodySnippet,
            binding.searchResultSubtitle
        )
        is Conversation -> {
            bindHighlight(
                query,
                model.conversation.recipient.name.orEmpty(),
                binding.searchResultTitle
            )
            // TODO: monitor performance and improve
            val groupMembers = DatabaseComponent.get(binding.root.context)
                .groupDatabase()
                .getGroupMembers(model.conversation.recipient.address.toGroupString(), false)

            val membersString = groupMembers.joinToString {
                val address = it.address.serialize()
                it.name ?: "${address.take(4)}...${address.takeLast(4)}"
            }
            bindHighlight(query, membersString, binding.searchResultSubtitle)
        }
    }
}

private fun bindHighlight(query: String?, toSearch: String, textView: TextView) {
    val normalizedQuery = query.orEmpty().lowercase()
    val normalizedSearch = toSearch.lowercase()

    textView.text =
        SearchUtil.getHighlightedSpan(Locale.getDefault(), BoldStyleFactory, toSearch, query)
}

fun ContentView.bindModel(query: String?, model: Conversation) {
    binding.searchResultSubtitle.isVisible = true
    binding.searchResultProfilePicture.update(model.conversation.recipient)
    val nameString = model.conversation.recipient.toShortString()
    bindHighlight(query, nameString, binding.searchResultTitle)

    val groupMembers = DatabaseComponent.get(binding.root.context)
        .groupDatabase()
        .getGroupMembers(model.conversation.recipient.address.toGroupString(), false)

    val membersString = groupMembers.joinToString {
        val address = it.address.serialize()
        it.name ?: "${address.take(4)}...${address.takeLast(4)}"
    }
    bindHighlight(query, membersString, binding.searchResultSubtitle)
}

fun ContentView.bindModel(query: String?, model: ContactModel) {

    binding.searchResultSubtitle.isVisible = false
    binding.searchResultSubtitle.text = null
    val recipient =
        Recipient.from(binding.root.context, Address.fromSerialized(model.contact.sessionID), false)
    binding.searchResultProfilePicture.update(recipient)
    val nameString = model.contact.getSearchName()
    bindHighlight(query, nameString, binding.searchResultTitle)
}

fun ContentView.bindModel(query: String?, model: Message) {
    binding.searchResultProfilePicture.update(model.messageResult.messageRecipient)
    binding.searchResultTitle.text = model.messageResult.messageRecipient.toShortString()
    binding.searchResultSubtitle.isVisible = true
    bindHighlight(query, model.messageResult.bodySnippet, binding.searchResultSubtitle)
}

fun Contact.getSearchName(): String =
    if (nickname.isNullOrEmpty()) name.orEmpty() else "${
        name ?: "${sessionID.take(4)}...${
            sessionID.takeLast(
                4
            )
        }"
    } ($nickname)"