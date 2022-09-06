package org.thoughtcrime.securesms.conversation.start

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.setPeekHeight
import com.afollestad.materialdialogs.customview.customView
import network.loki.messenger.databinding.DialogNewConversationBinding
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.contacts.ContactClickListener
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem
import org.thoughtcrime.securesms.mms.GlideApp

object StartConversation {

    private val defaultPeekHeight: Int by lazy { (Resources.getSystem().displayMetrics.heightPixels * 0.94).toInt() }

    fun showDialog(contacts: List<ContactSelectionListItem>, context: Context, delegate: StartConversationDelegate) {
        val dialog = MaterialDialog(context, BottomSheet())
        dialog.show {
            val binding = DialogNewConversationBinding.inflate(LayoutInflater.from(context))
            customView(view = binding.root)
            binding.closeButton.setOnClickListener { dismiss() }
            binding.newMessageButton.setOnClickListener { delegate.createNewMessage() }
            binding.newGroupButton.setOnClickListener { delegate.createNewGroup() }
            binding.joinCommunityButton.setOnClickListener { delegate.joinCommunity() }
            val adapter = ContactSelectionListAdapter(context, false).apply {
                glide = GlideApp.with(context)
            }
            adapter.contactClickListener = object : ContactClickListener {
                override fun onContactClick(contact: Recipient) {
                    adapter.onContactClick(contact)
                }

                override fun onContactSelected(contact: Recipient) {
                    delegate.contactSelected(contact.address.serialize())
                }

                override fun onContactDeselected(contact: Recipient) = Unit
            }
            binding.contactsRecyclerView.adapter = adapter
            adapter.items = contacts
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

}

