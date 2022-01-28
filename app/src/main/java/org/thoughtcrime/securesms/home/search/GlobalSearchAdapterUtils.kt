package org.thoughtcrime.securesms.home.search

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
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
                val typedValue = TypedValue()
                val theme = binding.root.context.theme
                theme.resolveAttribute(R.attr.searchHighlightTint, typedValue, true)
                val color = typedValue.data
                val span = ForegroundColorSpan(color)
                val text = "${model.messageResult.messageRecipient.getSearchName()}: "
                textSpannable.append(text)
                textSpannable.setSpan(span,0,text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSpannable.append(getHighlight(
                    query,
                    model.messageResult.bodySnippet
            ))
            binding.searchResultSubtitle.text = textSpannable
            binding.searchResultSubtitle.isVisible = true
            binding.searchResultTitle.text = model.messageResult.conversationRecipient.toShortString()
        }
        is Conversation -> {
            binding.searchResultTitle.text = getHighlight(
                query,
                model.conversation.recipient.name.orEmpty()
            )
            // TODO: monitor performance and improve
            val groupMembers = DatabaseComponent.get(binding.root.context)
                .groupDatabase()
                .getGroupMembers(model.conversation.recipient.address.toGroupString(), false)

            val membersString = groupMembers.joinToString {
                val address = it.address.serialize()
                it.name ?: "${address.take(4)}...${address.takeLast(4)}"
            }
            binding.searchResultSubtitle.text = getHighlight(query, membersString)
        }
    }
}

private fun getHighlight(query: String?, toSearch: String): Spannable? {
    return SearchUtil.getHighlightedSpan(Locale.getDefault(), BoldStyleFactory, toSearch, query)
}

fun ContentView.bindModel(query: String?, model: Conversation) {
    binding.searchResultSubtitle.isVisible = true
    binding.searchResultProfilePicture.update(model.conversation.recipient)
    val nameString = model.conversation.recipient.toShortString()
    binding.searchResultTitle.text = getHighlight(query, nameString)

    val groupMembers = DatabaseComponent.get(binding.root.context)
        .groupDatabase()
        .getGroupMembers(model.conversation.recipient.address.toGroupString(), false)

    val membersString = groupMembers.joinToString {
        val address = it.address.serialize()
        it.name ?: "${address.take(4)}...${address.takeLast(4)}"
    }
    binding.searchResultSubtitle.text = getHighlight(query, membersString)
}

fun ContentView.bindModel(query: String?, model: ContactModel) {

    binding.searchResultSubtitle.isVisible = false
    binding.searchResultSubtitle.text = null
    val recipient =
        Recipient.from(binding.root.context, Address.fromSerialized(model.contact.sessionID), false)
    binding.searchResultProfilePicture.update(recipient)
    val nameString = model.contact.getSearchName()
    binding.searchResultTitle.text = getHighlight(query, nameString)
}

fun ContentView.bindModel(query: String?, model: Message) {
    binding.searchResultProfilePicture.update(model.messageResult.conversationRecipient)
    val textSpannable = SpannableStringBuilder()
    if (model.messageResult.conversationRecipient != model.messageResult.messageRecipient) {
        // group chat, bind
        val typedValue = TypedValue()
        val theme = binding.root.context.theme
        theme.resolveAttribute(R.attr.searchHighlightTint, typedValue, true)
        val color = typedValue.data
        val span = ForegroundColorSpan(color)
        val text = "${model.messageResult.messageRecipient.getSearchName()}: "
        textSpannable.append(text)
        textSpannable.setSpan(span,0,text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    textSpannable.append(getHighlight(
            query,
            model.messageResult.bodySnippet
    ))
    binding.searchResultSubtitle.text = textSpannable
    binding.searchResultTitle.text = model.messageResult.conversationRecipient.toShortString()
    binding.searchResultSubtitle.isVisible = true
}

fun Recipient.getSearchName(): String = name ?: address.serialize().let { address -> "${address.take(4)}...${address.takeLast(4)}" }

fun Contact.getSearchName(): String =
        if (nickname.isNullOrEmpty()) name ?: "${sessionID.take(4)}...${sessionID.takeLast(4)}"
        else "${name ?: "${sessionID.take(4)}...${sessionID.takeLast(4)}"} ($nickname)"