package org.thoughtcrime.securesms.conversation.start

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.setPeekHeight
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogCreateClosedGroupBinding
import network.loki.messenger.databinding.DialogCreatePrivateChatBinding
import network.loki.messenger.databinding.DialogJoinCommunityBinding
import network.loki.messenger.databinding.DialogNewConversationBinding
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.groupSizeLimit
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.contacts.SelectContactsAdapter
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.dms.CreatePrivateChatFragmentAdapter
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.JoinCommunityFragmentAdapter
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.fadeIn
import org.thoughtcrime.securesms.util.fadeOut

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
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.cell_background))
            val binding = DialogNewConversationBinding.inflate(LayoutInflater.from(context))
            customView(view = binding.root, scrollable = true, noVerticalPadding = true)
            binding.closeButton.setOnClickListener { dismiss() }
            binding.createPrivateChatButton.setOnClickListener { delegate.onNewMessageSelected(); dismiss() }
            binding.createClosedGroupButton.setOnClickListener { delegate.onCreateGroupSelected(); dismiss() }
            binding.joinCommunityButton.setOnClickListener { delegate.onJoinCommunitySelected(); dismiss() }
            val adapter = ContactListAdapter(context, GlideApp.with(context)) {
                delegate.onContactSelected(it.address.serialize())
                dismiss()
            }
            adapter.items = contactGroups.flatMap { entry -> listOf(ContactListItem.Header(entry.key)) + entry.value }
            binding.contactsRecyclerView.adapter = adapter
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

    fun showPrivateChatCreationDialog(activity: FragmentActivity, delegate: StartConversationDelegate) {
        val dialog = MaterialDialog(activity, BottomSheet())
        dialog.show {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.cell_background))
            val binding = DialogCreatePrivateChatBinding.inflate(LayoutInflater.from(activity))
            customView(view = binding.root, noVerticalPadding = true)
            binding.backButton.setOnClickListener { delegate.onDialogBackPressed(); dismiss() }
            binding.closeButton.setOnClickListener { dismiss() }
            fun showLoader() {
                binding.loader.visibility = View.VISIBLE
                binding.loader.animate().setDuration(150).alpha(1.0f).start()
            }
            fun hideLoader() {
                binding.loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        binding.loader.visibility = View.GONE
                    }
                })
            }
            val enterPublicKeyDelegate = { publicKey: String -> createPrivateChat(publicKey, activity) }
            val adapter = CreatePrivateChatFragmentAdapter(activity, enterPublicKeyDelegate) { onsNameOrPublicKey ->
                if (PublicKeyValidation.isValid(onsNameOrPublicKey)) {
                    createPrivateChat(onsNameOrPublicKey, activity)
                } else {
                    // This could be an ONS name
                    showLoader()
                    SnodeAPI.getSessionID(onsNameOrPublicKey).successUi { hexEncodedPublicKey ->
                        hideLoader()
                        createPrivateChat(hexEncodedPublicKey, activity)
                        dismiss()
                    }.failUi { exception ->
                        hideLoader()
                        var message = activity.resources.getString(R.string.fragment_enter_public_key_error_message)
                        exception.localizedMessage?.let {
                            message = it
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            binding.viewPager.adapter = adapter
            val mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
                tab.text = when (pos) {
                    0 -> activity.resources.getString(R.string.activity_create_private_chat_enter_session_id_tab_title)
                    1 -> activity.resources.getString(R.string.activity_create_private_chat_scan_qr_code_tab_title)
                    else -> throw IllegalStateException()
                }
            }
            mediator.attach()
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

    private fun createPrivateChat(hexEncodedPublicKey: String, activity: FragmentActivity) {
        val recipient = Recipient.from(activity, Address.fromSerialized(hexEncodedPublicKey), false)
        val intent = Intent(activity, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
        intent.setDataAndType(activity.intent.data, activity.intent.type)
        val existingThread = DatabaseComponent.get(activity).threadDatabase().getThreadIdIfExistsFor(recipient)
        intent.putExtra(ConversationActivityV2.THREAD_ID, existingThread)
        activity.startActivity(intent)
    }

    fun showClosedGroupCreationDialog(members: List<String>, context: Context, delegate: StartConversationDelegate) {
        val dialog = MaterialDialog(context, BottomSheet())
        dialog.show {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.cell_background))
            val binding = DialogCreateClosedGroupBinding.inflate(LayoutInflater.from(context))
            customView(view = binding.root, scrollable = true, noVerticalPadding = true)
            val adapter = SelectContactsAdapter(context, GlideApp.with(context)).apply {
                this.members = members
            }
            binding.backButton.setOnClickListener { delegate.onDialogBackPressed(); dismiss() }
            binding.closeButton.setOnClickListener { dismiss() }
            binding.contactSearch.callbacks = object : KeyboardPageSearchView.Callbacks {
                override fun onQueryChanged(query: String) {
                    adapter.members = members.filter { it.contains(query) }
                }
            }
            binding.createNewPrivateChatButton.setOnClickListener { delegate.onNewMessageSelected(); dismiss() }
            binding.recyclerView.adapter = adapter
            var isLoading = false
            binding.createClosedGroupButton.setOnClickListener {
                if (isLoading) return@setOnClickListener
                val name = binding.nameEditText.text.trim()
                if (name.isEmpty()) {
                    return@setOnClickListener Toast.makeText(context, R.string.activity_create_closed_group_group_name_missing_error, Toast.LENGTH_LONG).show()
                }
                if (name.length >= 30) {
                    return@setOnClickListener Toast.makeText(context, R.string.activity_create_closed_group_group_name_too_long_error, Toast.LENGTH_LONG).show()
                }
                val selectedMembers = adapter.selectedMembers
                if (selectedMembers.isEmpty()) {
                    return@setOnClickListener Toast.makeText(context, R.string.activity_create_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
                }
                if (selectedMembers.count() >= groupSizeLimit) { // Minus one because we're going to include self later
                    return@setOnClickListener Toast.makeText(context, R.string.activity_create_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
                }
                val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
                isLoading = true
                binding.loaderContainer.fadeIn()
                MessageSender.createClosedGroup(name.toString(), selectedMembers + setOf( userPublicKey )).successUi { groupID ->
                    binding.loaderContainer.fadeOut()
                    isLoading = false
                    val threadID = DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(Recipient.from(context, Address.fromSerialized(groupID), false))
                    openConversationActivity(
                        context,
                        threadID,
                        Recipient.from(context, Address.fromSerialized(groupID), false)
                    )
                    dismiss()
                }.failUi {
                    binding.loaderContainer.fadeOut()
                    isLoading = false
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }
            }
            binding.mainContentGroup.isVisible = members.isNotEmpty()
            binding.emptyStateGroup.isVisible = members.isEmpty()
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

    private fun openConversationActivity(context: Context, threadId: Long, recipient: Recipient) {
        val intent = Intent(context, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
        context.startActivity(intent)
    }

    fun showJoinCommunityDialog(activity: FragmentActivity, delegate: StartConversationDelegate) {
        val dialog = MaterialDialog(activity, BottomSheet())
        dialog.show {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.cell_background))
            val binding = DialogJoinCommunityBinding.inflate(LayoutInflater.from(activity))
            customView(view = binding.root, noVerticalPadding = true)
            binding.backButton.setOnClickListener { delegate.onDialogBackPressed(); dismiss() }
            binding.closeButton.setOnClickListener { dismiss() }
            fun showLoader() {
                binding.loader.visibility = View.VISIBLE
                binding.loader.animate().setDuration(150).alpha(1.0f).start()
            }

            fun hideLoader() {
                binding.loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        binding.loader.visibility = View.GONE
                    }
                })
            }
            fun joinCommunityIfPossible(url: String) {
                val openGroup = try {
                    OpenGroupUrlParser.parseUrl(url)
                } catch (e: OpenGroupUrlParser.Error) {
                    when (e) {
                        is OpenGroupUrlParser.Error.MalformedURL -> return Toast.makeText(activity, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
                        is OpenGroupUrlParser.Error.InvalidPublicKey -> return Toast.makeText(activity, R.string.invalid_public_key, Toast.LENGTH_SHORT).show()
                        is OpenGroupUrlParser.Error.NoPublicKey -> return Toast.makeText(activity, R.string.invalid_public_key, Toast.LENGTH_SHORT).show()
                        is OpenGroupUrlParser.Error.NoRoom -> return Toast.makeText(activity, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
                    }
                }
                showLoader()
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val sanitizedServer = openGroup.server.removeSuffix("/")
                        val openGroupID = "$sanitizedServer.${openGroup.room}"
                        OpenGroupManager.add(sanitizedServer, openGroup.room, openGroup.serverPublicKey, activity)
                        val storage = MessagingModuleConfiguration.shared.storage
                        storage.onOpenGroupAdded(sanitizedServer)
                        val threadID = GroupManager.getOpenGroupThreadID(openGroupID, activity)
                        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())

                        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(activity)
                        withContext(Dispatchers.Main) {
                            val recipient = Recipient.from(activity, Address.fromSerialized(groupID), false)
                            openConversationActivity(activity, threadID, recipient)
                            dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e("Loki", "Couldn't join open group.", e)
                        withContext(Dispatchers.Main) {
                            hideLoader()
                            Toast.makeText(activity, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }
            }
            val enterCommunityUrlDelegate = { url: String -> joinCommunityIfPossible(url) }
            binding.viewPager.adapter = JoinCommunityFragmentAdapter(activity, enterCommunityUrlDelegate) { url ->
                joinCommunityIfPossible(url)
            }
            val mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
                tab.text = when (pos) {
                    0 -> activity.resources.getString(R.string.activity_join_public_chat_enter_community_url_tab_title)
                    1 -> activity.resources.getString(R.string.activity_join_public_chat_scan_qr_code_tab_title)
                    else -> throw IllegalStateException()
                }
            }
            mediator.attach()
        }
        dialog.setPeekHeight(defaultPeekHeight)
    }

}

