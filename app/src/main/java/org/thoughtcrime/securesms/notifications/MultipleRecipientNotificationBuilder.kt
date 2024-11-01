package org.thoughtcrime.securesms.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import androidx.core.app.NotificationCompat
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.NotificationPrivacyPreference
import org.session.libsession.utilities.StringSubstitutionConstants.CONVERSATION_COUNT_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.MESSAGE_COUNT_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.Util.getBoldedString
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.ui.getSubbedString
import java.util.LinkedList

class MultipleRecipientNotificationBuilder(context: Context, privacy: NotificationPrivacyPreference?) : AbstractNotificationBuilder(context, privacy) {
    private val messageBodies: MutableList<CharSequence> = LinkedList()

    init {
        color = context.resources.getColor(R.color.textsecure_primary)
        setSmallIcon(R.drawable.ic_notification)
        setContentTitle(context.getString(R.string.app_name))
        setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        setCategory(NotificationCompat.CATEGORY_MESSAGE)
        setGroupSummary(true)
    }

    fun setMessageCount(messageCount: Int, threadCount: Int) {
        val txt = context.getSubbedString(R.string.notificationsSystem, MESSAGE_COUNT_KEY to messageCount.toString(), CONVERSATION_COUNT_KEY to threadCount.toString())
        setSubText(txt)
        setNumber(messageCount)
    }

    fun setMostRecentSender(recipient: Recipient, threadRecipient: Recipient) {
        var displayName = recipient.toShortString()
        if (threadRecipient.isGroupOrCommunityRecipient) {
            displayName = getGroupDisplayName(recipient, threadRecipient.isCommunityRecipient)
        }
        if (privacy.isDisplayContact) {
            val txt = Phrase.from(context, R.string.notificationsMostRecent)
                .put(NAME_KEY, displayName)
                .format().toString()
            setContentText(txt)
        }

        if (recipient.notificationChannel != null) {
            setChannelId(recipient.notificationChannel!!)
        }
    }

    fun addActions(markAsReadIntent: PendingIntent?) {
        val markAllAsReadAction = NotificationCompat.Action(
            R.drawable.check,
            context.getString(R.string.messageMarkRead),
            markAsReadIntent
        )
        addAction(markAllAsReadAction)
        extend(NotificationCompat.WearableExtender().addAction(markAllAsReadAction))
    }

    fun putStringExtra(key: String?, value: String?) { extras.putString(key, value) }

    fun addMessageBody(sender: Recipient, threadRecipient: Recipient, body: CharSequence?) {
        var displayName = sender.toShortString()
        if (threadRecipient.isGroupOrCommunityRecipient) {
            displayName = getGroupDisplayName(sender, threadRecipient.isCommunityRecipient)
        }
        if (privacy.isDisplayMessage) {
            val builder = SpannableStringBuilder()
            builder.append(getBoldedString(displayName))
            builder.append(": ")
            builder.append(body ?: "")
            messageBodies.add(builder)
        } else if (privacy.isDisplayContact) {
            messageBodies.add(getBoldedString(displayName))
        }

        // TODO: What on earth is this? Why is it commented out? It's also commented out in dev... remove? -ACL 2024-08-29
        if (privacy.isDisplayContact && sender.contactUri != null) {
//      addPerson(sender.getContactUri().toString());
        }
    }

    override fun build(): Notification {
        if (privacy.isDisplayMessage || privacy.isDisplayContact) {
            val style = NotificationCompat.InboxStyle()
            for (body in messageBodies) { style.addLine(trimToDisplayLength(body)) }
            setStyle(style)
        }
        return super.build()
    }

    /**
     * @param recipient          the * individual * recipient for which to get the display name.
     * @param openGroupRecipient whether in an open group context
     */
    private fun getGroupDisplayName(recipient: Recipient, openGroupRecipient: Boolean): String {
        val contactDB = get(context).sessionContactDatabase()
        val accountID = recipient.address.serialize()
        val contact = contactDB.getContactWithAccountID(accountID) ?: return accountID
        val displayName = contact.displayName(if (openGroupRecipient) Contact.ContactContext.OPEN_GROUP else Contact.ContactContext.REGULAR)
        if (displayName == null) { return accountID }
        return displayName
    }
}
