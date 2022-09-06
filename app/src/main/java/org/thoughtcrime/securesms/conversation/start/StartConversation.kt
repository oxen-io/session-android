package org.thoughtcrime.securesms.conversation.start

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.setPeekHeight
import com.afollestad.materialdialogs.customview.customView
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogNewConversationBinding
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.contacts.ContactClickListener
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem
import org.thoughtcrime.securesms.mms.GlideApp

object StartConversation {

    private val defaultPeekHeight: Int by lazy { (Resources.getSystem().displayMetrics.heightPixels * 0.94).toInt() }

    fun showDialog(recipients: List<Recipient>, context: Context, delegate: StartConversationDelegate) {
        val contacts = recipients.sortedBy { it.address }
            .groupBy { it.address.serialize()[2] }
            .flatMap { entry -> listOf(ContactSelectionListItem.Header(entry.key.uppercase())) + entry.value.map { ContactSelectionListItem.Contact(it) }}
            .toMutableList()
        if (contacts.isNotEmpty()) {
            contacts.add(0, ContactSelectionListItem.Header(context.getString(R.string.new_conversation_contacts_title)))
        }
        val dialog = MaterialDialog(context, BottomSheet())
        dialog.show {
            val binding = DialogNewConversationBinding.inflate(LayoutInflater.from(context))
            customView(view = binding.root, scrollable = true, noVerticalPadding = true)
            binding.closeButton.setOnClickListener { dismiss() }
            binding.newMessageButton.setOnClickListener { delegate.createNewMessage() }
            binding.newGroupButton.setOnClickListener { delegate.createNewGroup() }
            binding.joinCommunityButton.setOnClickListener { delegate.joinCommunity() }
            binding.contactsRecyclerView.adapter = ContactSelectionListAdapter(context, false).apply {
                glide = GlideApp.with(context)
                items = contacts
                contactClickListener = object : ContactClickListener {
                    override fun onContactClick(contact: Recipient) {
                        delegate.contactSelected(contact.address.serialize())
                    }
                    override fun onContactSelected(contact: Recipient) = Unit
                    override fun onContactDeselected(contact: Recipient) = Unit
                }
            }
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

}

