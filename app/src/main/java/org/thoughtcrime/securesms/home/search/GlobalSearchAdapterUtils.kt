package org.thoughtcrime.securesms.home.search

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.truncateIdForDisplay
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.ContentView
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.GroupConversation
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Header
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Message
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.SavedMessages
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.SubHeader
import org.thoughtcrime.securesms.util.DateUtils
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
        is ContactModel -> {
            binding.searchResultTitle.text = getHighlight(
                query,
                model.contact.getSearchName()
            )
        }
        is Message -> {
            val textSpannable = SpannableStringBuilder()
            if (model.messageResult.conversationRecipient != model.messageResult.messageRecipient) {
                // group chat, bind
                val text = "${model.messageResult.messageRecipient.getSearchName()}: "
                textSpannable.append(text)
            }
            textSpannable.append(getHighlight(
                    query,
                    model.messageResult.bodySnippet
            ))
            binding.searchResultSubtitle.text = textSpannable
            binding.searchResultSubtitle.isVisible = true
            binding.searchResultTitle.text = model.messageResult.conversationRecipient.getSearchName()
        }
        is GroupConversation -> {
            binding.searchResultTitle.text = getHighlight(
                query,
                model.groupRecord.title
            )

            val membersString = model.groupRecord.members.joinToString { address ->
                Recipient.from(binding.root.context, address, false).getSearchName()
            }
            binding.searchResultSubtitle.text = getHighlight(query, membersString)
        }
        is Header, // do nothing for header
        is SubHeader, // do nothing for subheader
        is SavedMessages -> Unit // do nothing for saved messages (displays note to self)
    }
}

private fun getHighlight(query: String?, toSearch: String): Spannable? {
    return SearchUtil.getHighlightedSpan(Locale.getDefault(), BoldStyleFactory, toSearch, query)
}

fun ContentView.bindModel(query: String?, model: GroupConversation) {
    binding.searchResultProfilePicture.isVisible = true
    binding.searchResultSubtitle.isVisible = model.groupRecord.isClosedGroup
    binding.searchResultTimestamp.isVisible = false
    val threadRecipient = Recipient.from(binding.root.context, Address.fromSerialized(model.groupRecord.encodedId), false)
    binding.searchResultProfilePicture.update(threadRecipient)
    val nameString = model.groupRecord.title
    binding.searchResultTitle.text = getHighlight(query, nameString)

    val groupRecipients = model.groupRecord.members.map { Recipient.from(binding.root.context, it, false) }

    val membersString = groupRecipients.joinToString(transform = Recipient::getSearchName)
    if (model.groupRecord.isClosedGroup) {
        binding.searchResultSubtitle.text = getHighlight(query, membersString)
    }
}

fun ContentView.bindModel(query: String?, model: ContactModel) = binding.run {
    searchResultProfilePicture.isVisible = true
    searchResultSubtitle.isVisible = false
    searchResultTimestamp.isVisible = false
    searchResultSubtitle.text = null
    val recipient = Recipient.from(root.context, Address.fromSerialized(model.contact.accountID), false)
    searchResultProfilePicture.update(recipient)
    val nameString = if (model.isSelf) root.context.getString(R.string.note_to_self)
        else model.contact.getSearchName()
    searchResultTitle.text = getHighlight(query, nameString)
}

fun ContentView.bindModel(model: SavedMessages) {
    binding.searchResultSubtitle.isVisible = false
    binding.searchResultTimestamp.isVisible = false
    binding.searchResultTitle.setText(R.string.note_to_self)
    binding.searchResultProfilePicture.update(Address.fromSerialized(model.currentUserPublicKey))
    binding.searchResultProfilePicture.isVisible = true
}

fun ContentView.bindModel(query: String?, model: Message) = binding.apply {
    searchResultProfilePicture.isVisible = true
    searchResultTimestamp.isVisible = true
//    val hasUnreads = model.unread > 0
//    unreadCountIndicator.isVisible = hasUnreads
//    if (hasUnreads) {
//        unreadCountTextView.text = model.unread.toString()
//    }
    searchResultTimestamp.text = DateUtils.getDisplayFormattedTimeSpanString(root.context, Locale.getDefault(), model.messageResult.sentTimestampMs)
    searchResultProfilePicture.update(model.messageResult.conversationRecipient)
    val textSpannable = SpannableStringBuilder()
    if (model.messageResult.conversationRecipient != model.messageResult.messageRecipient) {
        // group chat, bind
        val text = "${model.messageResult.messageRecipient.toShortString()}: "
        textSpannable.append(text)
    }
    textSpannable.append(getHighlight(
            query,
            model.messageResult.bodySnippet
    ))
    searchResultSubtitle.text = textSpannable
    searchResultTitle.text = if (model.isSelf) root.context.getString(R.string.note_to_self)
        else model.messageResult.conversationRecipient.getSearchName()
    searchResultSubtitle.isVisible = true
}

fun Recipient.getSearchName(): String =
    name?.takeIf { it.isNotEmpty() && !it.looksLikeAccountId }
    ?: address.serialize().let(::truncateIdForDisplay)

fun Contact.getSearchName(): String =
    nickname?.takeIf { it.isNotEmpty() && !it.looksLikeAccountId }
    ?: name?.takeIf { it.isNotEmpty() && !it.looksLikeAccountId }
    ?: truncateIdForDisplay(accountID)

private val String.looksLikeAccountId: Boolean get() = length > 60 && all { it.isDigit() || it.isLetter() }
