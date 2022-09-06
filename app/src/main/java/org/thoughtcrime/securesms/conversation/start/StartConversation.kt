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
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp

object StartConversation {

    private val defaultPeekHeight: Int by lazy { (Resources.getSystem().displayMetrics.heightPixels * 0.94).toInt() }

    fun showDialog(recipients: List<Recipient>, context: Context, delegate: StartConversationDelegate) {
        val unknownSectionTitle = context.getString(R.string.new_conversation_unknown_contacts_section_title)
        val contactGroups = recipients.map {
            val sessionId = it.address.serialize()
            val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(sessionId)
            val displayName = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionId
            ContactListItem.Contact(it, displayName)
        }.sortedBy { it.displayName }
            .groupBy { if (PublicKeyValidation.isValid(it.displayName)) unknownSectionTitle else it.displayName.first().uppercase() }
            .toMutableMap()
        contactGroups.remove(unknownSectionTitle)?.let { contactGroups.put(unknownSectionTitle, it) }

        val dialog = MaterialDialog(context, BottomSheet())
        dialog.show {
            val binding = DialogNewConversationBinding.inflate(LayoutInflater.from(context))
            customView(view = binding.root, scrollable = true, noVerticalPadding = true)
            binding.closeButton.setOnClickListener { dismiss() }
            binding.newMessageButton.setOnClickListener { delegate.createNewMessage() }
            binding.newGroupButton.setOnClickListener { delegate.createNewGroup() }
            binding.joinCommunityButton.setOnClickListener { delegate.joinCommunity() }
            val adapter = ContactListAdapter(context, GlideApp.with(context)) {
                delegate.contactSelected(it.address.serialize())
            }
            adapter.items = contactGroups.flatMap { entry -> listOf(ContactListItem.Header(entry.key)) + entry.value }
            binding.contactsRecyclerView.adapter = adapter
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

}

