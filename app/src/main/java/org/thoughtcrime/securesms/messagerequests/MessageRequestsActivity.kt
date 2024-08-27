package org.thoughtcrime.securesms.messagerequests

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityMessageRequestsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.push
import javax.inject.Inject

@AndroidEntryPoint
class MessageRequestsActivity : PassphraseRequiredActionBarActivity(), ConversationClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private lateinit var binding: ActivityMessageRequestsBinding
    private lateinit var glide: RequestManager

    @Inject lateinit var threadDb: ThreadDatabase

    private val viewModel: MessageRequestsViewModel by viewModels()

    private val adapter: MessageRequestsAdapter by lazy {
        MessageRequestsAdapter(context = this, cursor = threadDb.unapprovedConversationList, listener = this)
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityMessageRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        glide = Glide.with(this)

        adapter.setHasStableIds(true)
        adapter.glide = glide
        binding.recyclerView.adapter = adapter

        binding.clearAllMessageRequestsButton.setOnClickListener { deleteAll() }
    }

    override fun onResume() {
        super.onResume()
        LoaderManager.getInstance(this).restartLoader(0, null, this)
    }

    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
        return MessageRequestsLoader(this@MessageRequestsActivity)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        adapter.changeCursor(cursor)
        updateEmptyState()
    }

    override fun onLoaderReset(cursor: Loader<Cursor>) {
        adapter.changeCursor(null)
    }

    override fun onConversationClick(thread: ThreadRecord) {
        val intent = Intent(this, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.THREAD_ID, thread.threadId)
        push(intent)
    }

    override fun onBlockConversationClick(thread: ThreadRecord) {
        fun doBlock() {
            viewModel.blockMessageRequest(thread)
            LoaderManager.getInstance(this).restartLoader(0, null, this)
        }

        showSessionDialog {
            title(R.string.RecipientPreferenceActivity_block_this_contact_question)
                text(R.string.message_requests_block_message)
                button(R.string.recipient_preferences__block) { doBlock() }
                button(R.string.no)
        }
    }

    override fun onDeleteConversationClick(thread: ThreadRecord) {
        fun doDecline() {
            viewModel.deleteMessageRequest(thread)
            LoaderManager.getInstance(this).restartLoader(0, null, this)
            lifecycleScope.launch(Dispatchers.IO) {
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@MessageRequestsActivity)
            }
        }

        showSessionDialog {
            title(R.string.decline)
            text(resources.getString(R.string.message_requests_decline_message))
            button(R.string.decline) { doDecline() }
            button(R.string.no)
        }
    }

    private fun updateEmptyState() {
        val threadCount = (binding.recyclerView.adapter as MessageRequestsAdapter).itemCount
        binding.emptyStateContainer.isVisible = threadCount == 0
        binding.clearAllMessageRequestsButton.isVisible = threadCount != 0
    }

    private fun deleteAll() {
        fun doDeleteAllAndBlock() {
            viewModel.clearAllMessageRequests(false)
            LoaderManager.getInstance(this).restartLoader(0, null, this)
            lifecycleScope.launch(Dispatchers.IO) {
                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@MessageRequestsActivity)
            }
        }

        showSessionDialog {
            text(resources.getString(R.string.message_requests_clear_all_message))
            button(R.string.yes) { doDeleteAllAndBlock() }
            button(R.string.no)
        }
    }
}
