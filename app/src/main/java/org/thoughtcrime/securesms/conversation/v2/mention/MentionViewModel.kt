package org.thoughtcrime.securesms.conversation.v2.mention

import android.content.ContentResolver
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.text.getSpans
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.database.DatabaseContentProviders.Conversation
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.GroupMemberDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.util.observeChanges

/**
 * A ViewModel that provides the mention search functionality for a text input.
 *
 * To use this ViewModel, you (a view) will need to:
 * 1. Observe the [autoCompleteState] to get the mention search results.
 * 2. Set the EditText's editable factory to [editableFactory], via [android.widget.EditText.setEditableFactory]
 */
class MentionViewModel(
    threadID: Long,
    contentResolver: ContentResolver,
    threadDatabase: ThreadDatabase,
    groupDatabase: GroupDatabase,
    mmsDatabase: MmsDatabase,
    contactDatabase: SessionContactDatabase,
    memberDatabase: GroupMemberDatabase,
    storage: Storage,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val editable = MentionEditable()

    /**
     * A factory that creates a new [Editable] instance that is backed by the same source of truth
     * used by this viewModel.
     */
    val editableFactory = object : Editable.Factory() {
        override fun newEditable(source: CharSequence?): Editable {
            if (source === editable) {
                return source
            }

            if (source != null) {
                editable.replace(0, editable.length, source)
            }

            return editable
        }
    }

    @Suppress("OPT_IN_USAGE")
    private val members: StateFlow<List<Member>?> =
        (contentResolver.observeChanges(Conversation.getUriForThread(threadID)) as Flow<Any?>)
            .debounce(500L)
            .onStart { emit(Unit) }
            .mapLatest {
                val recipient = checkNotNull(threadDatabase.getRecipientForThreadId(threadID)) {
                    "Recipient not found for thread ID: $threadID"
                }

                val memberIDs = when {
                    recipient.isClosedGroupRecipient -> {
                        groupDatabase.getGroupMemberAddresses(recipient.address.toGroupString(), false)
                            .map { it.serialize() }
                    }

                    recipient.isCommunityRecipient -> mmsDatabase.getRecentChatMemberIDs(threadID, 20)
                    recipient.isContactRecipient -> listOf(recipient.address.serialize())
                    else -> listOf()
                }

                val moderatorIDs = if (recipient.isCommunityRecipient) {
                    val groupId = storage.getOpenGroup(threadID)?.id
                    if (groupId.isNullOrBlank()) {
                        emptySet()
                    } else {
                        memberDatabase.getGroupMembersRoles(groupId, memberIDs)
                            .mapNotNullTo(hashSetOf()) { (memberId, roles) ->
                                memberId.takeIf { roles.any { it.isModerator } }
                            }
                    }
                } else {
                    emptySet()
                }

                val contactContext = if (recipient.isCommunityRecipient) {
                    Contact.ContactContext.OPEN_GROUP
                } else {
                    Contact.ContactContext.REGULAR
                }

                contactDatabase.getContacts(memberIDs).map { contact ->
                    Member(
                        publicKey = contact.sessionID,
                        name = contact.displayName(contactContext).orEmpty(),
                        isModerator = contact.sessionID in moderatorIDs,
                    )
                }
            }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000L), null)


    @OptIn(ExperimentalCoroutinesApi::class)
    val autoCompleteState: StateFlow<AutoCompleteState> = editable
        .observeMentionSearchQuery()
        .flatMapLatest { query ->
            if (query == null) {
                return@flatMapLatest flowOf(AutoCompleteState.Idle)
            }

            members.mapLatest { members ->
                if (members == null) {
                    return@mapLatest AutoCompleteState.Loading
                }

                withContext(Dispatchers.Default) {
                    val filtered = if (query.query.isBlank()) {
                        members.mapTo(mutableListOf()) { Candidate(it, it.name, 0) }
                    } else {
                        members.mapNotNullTo(mutableListOf()) { searchAndHighlight(it, query.query) }
                    }

                    filtered.sortWith(Candidate.MENTION_LIST_COMPARATOR)
                    AutoCompleteState.Result(filtered, query.query)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AutoCompleteState.Idle)

    private fun searchAndHighlight(
        haystack: Member,
        needle: String
    ): Candidate? {
        val startIndex = haystack.name.indexOf(needle, ignoreCase = true)

        return if (startIndex >= 0) {
            val endIndex = startIndex + needle.length
            val spanned = SpannableStringBuilder(haystack.name)
            spanned.setSpan(
                StyleSpan(Typeface.BOLD),
                startIndex,
                endIndex,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            Candidate(member = haystack, nameHighlighted = spanned, matchScore = startIndex)
        } else {
            null
        }
    }

    fun onCandidateSelected(candidatePublicKey: String) {
        val query = editable.mentionSearchQuery ?: return
        val autoCompleteState = autoCompleteState.value as? AutoCompleteState.Result ?: return
        val candidate = autoCompleteState.members.find { it.member.publicKey == candidatePublicKey } ?: return

        editable.addMention(
            candidate.member,
            query.mentionSymbolStartAt .. (query.mentionSymbolStartAt + query.query.length + 1)
        )
    }

    /**
     * Given a message body, normalize it by replacing the display name following '@' with their public key.
     *
     * As "@123456" is the standard format for mentioning a user, this method will replace "@Alice" with "@123456"
     */
    fun normalizeMessageBody(): String {
        val spansWithRanges = editable.getSpans<MentionSpan>()
            .mapTo(mutableListOf()) { span ->
                span to (editable.getSpanStart(span)..editable.getSpanEnd(span))
            }

        spansWithRanges.sortBy { it.second.first }

        val sb = StringBuilder()
        var offset = 0
        for ((span, range) in spansWithRanges) {
            // Add content before the mention span
            sb.append(editable, offset, range.first)

            // Replace the mention span with "@public key"
            sb.append('@').append(span.member.publicKey).append(' ')

            offset = range.last + 1
        }

        // Add the remaining content
        sb.append(editable, offset, editable.length)
        return sb.toString()
    }

    data class Member(
        val publicKey: String,
        val name: String,
        val isModerator: Boolean,
    )

    data class Candidate(
        val member: Member,
        // The name with the matching keyword highlighted.
        val nameHighlighted: CharSequence,
        // The score of matching the query keyword. Lower is better.
        val matchScore: Int,
    ) {
        companion object {
            val MENTION_LIST_COMPARATOR = compareBy<Candidate> { it.matchScore }
                .then(compareBy { it.member.name })
        }
    }

    sealed interface AutoCompleteState {
        object Idle : AutoCompleteState
        object Loading : AutoCompleteState
        data class Result(val members: List<Candidate>, val query: String) : AutoCompleteState
        object Error : AutoCompleteState
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val contentResolver: ContentResolver,
        private val threadDatabase: ThreadDatabase,
        private val groupDatabase: GroupDatabase,
        private val mmsDatabase: MmsDatabase,
        private val contactDatabase: SessionContactDatabase,
        private val storage: Storage,
        private val memberDatabase: GroupMemberDatabase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MentionViewModel(
                threadID = threadId,
                contentResolver = contentResolver,
                threadDatabase = threadDatabase,
                groupDatabase = groupDatabase,
                mmsDatabase = mmsDatabase,
                contactDatabase = contactDatabase,
                memberDatabase = memberDatabase,
                storage = storage,
            ) as T
        }
    }
}