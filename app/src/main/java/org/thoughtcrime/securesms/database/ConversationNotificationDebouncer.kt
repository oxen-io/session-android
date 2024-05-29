package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import org.session.libsession.utilities.Debouncer
import org.thoughtcrime.securesms.ApplicationContext

class ConversationNotificationDebouncer(private val context: ApplicationContext) {
    private val threadIDs = mutableSetOf<Long>()
    private val handler = context.conversationListNotificationHandler
    private val debouncer = Debouncer(handler, 100)

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var shared: ConversationNotificationDebouncer

        @Synchronized
        fun get(context: Context): ConversationNotificationDebouncer {
            if (::shared.isInitialized) { return shared }
            shared = ConversationNotificationDebouncer(context.applicationContext as ApplicationContext)
            return shared
        }
    }

    fun notify(threadID: Long) {
        synchronized(threadIDs) {
            threadIDs.add(threadID)
        }

        debouncer.publish { publish() }
    }

    private fun publish() {
        val toNotify = synchronized(threadIDs) {
            val copy = threadIDs.toList()
            threadIDs.clear()
            copy
        }

        for (threadID in toNotify) {
            context.contentResolver.notifyChange(DatabaseContentProviders.Conversation.getUriForThread(threadID), null)
        }
    }
}