package org.thoughtcrime.securesms.groups

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityCreateClosedGroupBinding
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.groupSizeLimit
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.contacts.SelectContactsAdapter
import org.thoughtcrime.securesms.contacts.SelectContactsLoader
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.fadeIn
import org.thoughtcrime.securesms.util.fadeOut

class CreateClosedGroupActivity : PassphraseRequiredActionBarActivity(), LoaderManager.LoaderCallbacks<List<String>> {
    private lateinit var binding: ActivityCreateClosedGroupBinding
    private var isLoading = false
        set(newValue) { field = newValue; invalidateOptionsMenu() }
    private var members = listOf<String>()
        set(value) { field = value; selectContactsAdapter.members = value }
    private val publicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(this)!!
        }

    private val selectContactsAdapter by lazy {
        SelectContactsAdapter(this, GlideApp.with(this))
    }

    companion object {
        const val closedGroupCreatedResultCode = 100
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivityCreateClosedGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar!!.title = resources.getString(R.string.activity_create_closed_group_title)
        binding.recyclerView.adapter = this.selectContactsAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.createNewPrivateChatButton.setOnClickListener { createNewPrivateChat() }
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_done, menu)
        return members.isNotEmpty() && !isLoading
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<String>> {
        return SelectContactsLoader(this, setOf())
    }

    override fun onLoadFinished(loader: Loader<List<String>>, members: List<String>) {
        update(members)
    }

    override fun onLoaderReset(loader: Loader<List<String>>) {
        update(listOf())
    }

    private fun update(members: List<String>) {
        //if there is a Note to self conversation, it loads self in the list, so we need to remove it here
        this.members = members.minus(publicKey)
        binding.mainContentContainer.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.doneButton -> if (!isLoading) { createClosedGroup() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNewPrivateChat() {
        setResult(closedGroupCreatedResultCode)
        finish()
    }

    private fun createClosedGroup() {
        val name = binding.nameEditText.text.trim()
        if (name.isEmpty()) {
            return Toast.makeText(this, R.string.activity_create_closed_group_group_name_missing_error, Toast.LENGTH_LONG).show()
        }
        if (name.length >= 64) {
            return Toast.makeText(this, R.string.activity_create_closed_group_group_name_too_long_error, Toast.LENGTH_LONG).show()
        }
        val selectedMembers = this.selectContactsAdapter.selectedMembers
        if (selectedMembers.count() < 1) {
            return Toast.makeText(this, R.string.activity_create_closed_group_not_enough_group_members_error, Toast.LENGTH_LONG).show()
        }
        if (selectedMembers.count() >= groupSizeLimit) { // Minus one because we're going to include self later
            return Toast.makeText(this, R.string.activity_create_closed_group_too_many_group_members_error, Toast.LENGTH_LONG).show()
        }
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)!!
        isLoading = true
        binding.loaderContainer.fadeIn()
        MessageSender.createClosedGroup(name.toString(), selectedMembers + setOf( userPublicKey )).successUi { groupID ->
            binding.loaderContainer.fadeOut()
            isLoading = false
            val threadID = DatabaseComponent.get(this).threadDatabase().getOrCreateThreadIdFor(Recipient.from(this, Address.fromSerialized(groupID), false))
             if (!isFinishing) {
                openConversationActivity(this, threadID, Recipient.from(this, Address.fromSerialized(groupID), false))
                finish()
            }
        }.failUi {
            binding.loaderContainer.fadeOut()
            isLoading = false
            Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
        }
    }
    // endregion
}

// region Convenience
private fun openConversationActivity(context: Context, threadId: Long, recipient: Recipient) {
    val intent = Intent(context, ConversationActivityV2::class.java)
    intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
    intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
    context.startActivity(intent)
}
// endregion