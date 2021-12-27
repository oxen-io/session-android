package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.notifications.MarkReadReceiver

class ConversationViewModel(
    val threadId: Long,
    private val threadDb: ThreadDatabase,
    private val draftDb: DraftDatabase,
    application: Application
) : AndroidViewModel(application) {

    val recipient: Recipient by lazy {
        threadDb.getRecipientForThreadId(threadId)!!
    }

    fun markAllAsRead() {
        val messages = threadDb.setRead(threadId, true)
        if (recipient.isGroupRecipient) {
            for (message in messages) {
                MarkReadReceiver.scheduleDeletion(getApplication(), message.expirationInfo)
            }
        } else {
            MarkReadReceiver.process(getApplication(), messages)
        }
        ApplicationContext.getInstance(getApplication()).messageNotifier.updateNotification(getApplication(), false, 0)
    }

    fun saveDraft(text: String) {
        if (text.isEmpty()) return
        val drafts = DraftDatabase.Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        draftDb.insertDrafts(threadId, drafts)
    }

    fun getDraft(): String? {
        val drafts = draftDb.getDrafts(threadId)
        draftDb.clearDrafts(threadId)
        return drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val threadDb: ThreadDatabase,
        private val draftDb: DraftDatabase,
        private val application: Application
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(
                threadId,
                threadDb,
                draftDb,
                application
            ) as T
        }
    }
}