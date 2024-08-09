/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.session.libsession.utilities.WindowDebouncer
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.ConversationNotificationDebouncer.Companion.get
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

abstract class Database @SuppressLint("WrongConstant") constructor(
  @JvmField protected val context: Context,
  @JvmField protected var databaseHelper: SQLCipherOpenHelper
) {
    protected val readableDatabase: SQLiteDatabase = databaseHelper.readableDatabase
    protected val writableDatabase: SQLiteDatabase = databaseHelper.writableDatabase

    private val conversationListNotificationDebouncer: WindowDebouncer =
        ApplicationContext.getInstance(context).conversationListDebouncer
    private val conversationListUpdater = Runnable {
        context.contentResolver.notifyChange(DatabaseContentProviders.ConversationList.CONTENT_URI, null)
    }

    protected fun notifyConversationListeners(threadIds: Set<Long>) {
        threadIds.forEach(::notifyConversationListeners)
    }

    protected fun notifyConversationListeners(threadId: Long) {
        get(context).notify(threadId)
    }

    fun notifyConversationListListeners() {
        conversationListNotificationDebouncer.publish(conversationListUpdater)
    }

    protected fun notifyStickerListeners() {
        context.contentResolver.notifyChange(DatabaseContentProviders.Sticker.CONTENT_URI, null)
    }

    protected fun notifyStickerPackListeners() {
        context.contentResolver.notifyChange(DatabaseContentProviders.StickerPack.CONTENT_URI, null)
    }

    protected fun notifyRecipientListeners() {
        context.contentResolver.notifyChange(DatabaseContentProviders.Recipient.CONTENT_URI, null)
        notifyConversationListListeners()
    }

    protected fun setNotifyConversationListeners(cursor: Cursor, threadId: Long) {
        cursor.setNotificationUri(
            context.contentResolver,
            DatabaseContentProviders.Conversation.getUriForThread(threadId)
        )
    }

    protected fun setNotifyConversationListListeners(cursor: Cursor) {
        cursor.setNotificationUri(
            context.contentResolver,
            DatabaseContentProviders.ConversationList.CONTENT_URI
        )
    }

    protected fun registerAttachmentListeners(observer: ContentObserver) {
        context.contentResolver.registerContentObserver(
            DatabaseContentProviders.Attachment.CONTENT_URI,
            true,
            observer
        )
    }

    protected fun notifyAttachmentListeners() {
        context.contentResolver.notifyChange(DatabaseContentProviders.Attachment.CONTENT_URI, null)
    }

    fun reset(databaseHelper: SQLCipherOpenHelper) {
        this.databaseHelper = databaseHelper
    }

    companion object {
        const val ID_WHERE: String = "_id = ?"
    }
}
