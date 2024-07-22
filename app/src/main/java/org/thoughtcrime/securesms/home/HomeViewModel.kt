package org.thoughtcrime.securesms.home

import android.content.ContentResolver
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.observeChanges
import java.util.Locale
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext as ApplicationContextQualifier

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val threadDb: ThreadDatabase,
    private val contentResolver: ContentResolver,
    private val prefs: TextSecurePreferences,
    @ApplicationContextQualifier private val context: Context,
) : ViewModel() {
    // SharedFlow that emits whenever the user asks us to reload  the conversation
    private val manualReloadTrigger = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * A [StateFlow] that emits the list of threads and the typing status of each thread.
     *
     * This flow will emit whenever the user asks us to reload the conversation list or
     * whenever the conversation list changes.
     */
    val data: StateFlow<Data?> = combine(
        observeConversationList(),
        observeTypingStatus(),
        messageRequests(),
        ::Data
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun hasHiddenMessageRequests() = TextSecurePreferences.events
        .filter { it == TextSecurePreferences.HAS_HIDDEN_MESSAGE_REQUESTS }
        .map { prefs.hasHiddenMessageRequests() }
        .onStart { emit(prefs.hasHiddenMessageRequests()) }

    private fun observeTypingStatus(): Flow<Set<Long>> =
            ApplicationContext.getInstance(context).typingStatusRepository
                    .typingThreads
                    .asFlow()
                    .onStart { emit(emptySet()) }
                    .distinctUntilChanged()

    private fun messageRequests() = combine(
        unapprovedConversationCount(),
        hasHiddenMessageRequests(),
        latestUnapprovedConversationTimestamp(),
        ::createMessageRequests
    ).flowOn(Dispatchers.IO)

    private fun unapprovedConversationCount() = reloadTriggersAndContentChanges()
        .map { threadDb.unapprovedConversationCount }

    private fun latestUnapprovedConversationTimestamp() = reloadTriggersAndContentChanges()
        .map { threadDb.latestUnapprovedConversationTimestamp }

    @Suppress("OPT_IN_USAGE")
    private fun observeConversationList(): Flow<List<ThreadRecord>> = reloadTriggersAndContentChanges()
        .mapLatest { _ ->
            threadDb.approvedConversationList.use { openCursor ->
                threadDb.readerFor(openCursor).run { generateSequence { next }.toList() }
            }
        }
        .flowOn(Dispatchers.IO)

    @OptIn(FlowPreview::class)
    private fun reloadTriggersAndContentChanges() = merge(
        manualReloadTrigger,
        contentResolver.observeChanges(DatabaseContentProviders.ConversationList.CONTENT_URI)
    )
        .debounce(CHANGE_NOTIFICATION_DEBOUNCE_MILLS)
        .onStart { emit(Unit) }

    fun tryReload() = manualReloadTrigger.tryEmit(Unit)

    data class Data(
        val threads: List<ThreadRecord> = emptyList(),
        val typingThreadIDs: Set<Long> = emptySet(),
        val messageRequests: MessageRequests? = null
    )

    private fun createMessageRequests(
        count: Int,
        hidden: Boolean,
        timestamp: Long
    ) = if (count > 0 && !hidden) MessageRequests(
        count.toString(),
        DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), timestamp)
    ) else null

    data class MessageRequests(val count: String, val timestamp: String)

    companion object {
        private const val CHANGE_NOTIFICATION_DEBOUNCE_MILLS = 100L
    }
}
