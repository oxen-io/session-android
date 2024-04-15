package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Range
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.res.ResourcesCompat
import network.loki.messenger.R
import nl.komponents.kovenant.combine.Tuple2
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ThemeUtil
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.UiModeUtilities
import org.thoughtcrime.securesms.util.getAccentColor
import org.thoughtcrime.securesms.util.getColorResourceIdFromAttr
import org.thoughtcrime.securesms.util.getMessageTextColourAttr
import java.util.regex.Pattern

object MentionUtilities {

    @JvmStatic
    fun highlightMentions(text: CharSequence, threadID: Long, context: Context): String {
        return highlightMentions(text, false, threadID, context).toString() // isOutgoingMessage is irrelevant
    }

    @JvmStatic
    fun highlightMentions(text: CharSequence, isOutgoingMessage: Boolean, threadID: Long, context: Context): SpannableString {
        @Suppress("NAME_SHADOWING") var text = text
        val pattern = Pattern.compile("@[0-9a-fA-F]*")
        var matcher = pattern.matcher(text)
        val mentions = mutableListOf<Tuple2<Range<Int>, String>>()
        var startIndex = 0
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val openGroup = DatabaseComponent.get(context).storage().getOpenGroup(threadID)
        if (matcher.find(startIndex)) {
            while (true) {
                val publicKey = text.subSequence(matcher.start() + 1, matcher.end()).toString() // +1 to get rid of the @
                val isUserBlindedPublicKey = openGroup?.let { SodiumUtilities.sessionId(userPublicKey, publicKey, it.publicKey) } ?: false
                val userDisplayName: String? = if (publicKey.equals(userPublicKey, ignoreCase = true) || isUserBlindedPublicKey) {
                    context.getString(R.string.MessageRecord_you)
                } else {
                    val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(publicKey)
                    @Suppress("NAME_SHADOWING") val context = if (openGroup != null) Contact.ContactContext.OPEN_GROUP else Contact.ContactContext.REGULAR
                    contact?.displayName(context)
                }
                if (userDisplayName != null) {
                    text = text.subSequence(0, matcher.start()).toString() + "@" + userDisplayName + text.subSequence(matcher.end(), text.length)
                    val endIndex = matcher.start() + 1 + userDisplayName.length
                    startIndex = endIndex
                    mentions.add(Tuple2(Range.create(matcher.start(), endIndex), publicKey))
                } else {
                    startIndex = matcher.end()
                }
                matcher = pattern.matcher(text)
                if (!matcher.find(startIndex)) { break }
            }
        }
        val result = SpannableString(text)

        var mentionTextColour: Int? = null
        // In dark themes..
        if (ThemeUtil.isDarkTheme(context)) {
            // ..we use the standard outgoing message colour for outgoing messages..
            if (isOutgoingMessage) {
                val mentionTextColourAttributeId = getMessageTextColourAttr(true)
                val mentionTextColourResourceId = getColorResourceIdFromAttr(context, mentionTextColourAttributeId)
                mentionTextColour = ResourcesCompat.getColor(context.resources, mentionTextColourResourceId, context.theme)
            }
            else // ..but we use the accent colour for incoming messages (i.e., someone mentioning us)..
            {
                mentionTextColour = context.getAccentColor()
            }
        }
        else // ..while in light themes we always just use the incoming or outgoing message text colour for mentions.
        {
            val mentionTextColourAttributeId = getMessageTextColourAttr(isOutgoingMessage)
            val mentionTextColourResourceId = getColorResourceIdFromAttr(context, mentionTextColourAttributeId)
            mentionTextColour = ResourcesCompat.getColor(context.resources, mentionTextColourResourceId, context.theme)
        }

        for (mention in mentions) {
            result.setSpan(ForegroundColorSpan(mentionTextColour), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(StyleSpan(Typeface.BOLD), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // If we're using a light theme then we change the background colour of the mention to be the accent colour
            if (ThemeUtil.isLightTheme(context)) {
                val backgroundColour = context.getAccentColor();
                result.setSpan(BackgroundColorSpan(backgroundColour), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return result
    }
}