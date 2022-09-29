package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Range
import androidx.core.content.res.ResourcesCompat
import network.loki.messenger.R
import nl.komponents.kovenant.combine.Tuple2
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.UiModeUtilities
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
                val isYou = isYou(publicKey, userPublicKey, openGroup)
                val userDisplayName: String? = if (!isOutgoingMessage && isYou) {
                    context.getString(R.string.MessageRecord_you)
                } else {
                    val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(publicKey)
                    @Suppress("NAME_SHADOWING") val context = if (openGroup != null) Contact.ContactContext.OPEN_GROUP else Contact.ContactContext.REGULAR
                    contact?.displayName(context)
                }
                if (userDisplayName != null) {
                    val mention = if (isYou) " @$userDisplayName " else "@$userDisplayName"
                    text = text.subSequence(0, matcher.start()).toString() + mention + text.subSequence(matcher.end(), text.length)
                    val endIndex = matcher.start() + 1 + userDisplayName.length
                    startIndex = endIndex
                    val upper = endIndex + if (isYou) 2 else 0
                    mentions.add(Tuple2(Range.create(matcher.start(), upper), publicKey))
                } else {
                    startIndex = matcher.end()
                }
                matcher = pattern.matcher(text)
                if (!matcher.find(startIndex)) { break }
            }
        }
        val result = SpannableString(text)
        val isLightMode = UiModeUtilities.isDayUiMode(context)
        for (mention in mentions) {
            var backgroundColorID = R.color.transparent
            val foregroundColorID: Int
            if (isYou(mention.second, userPublicKey, openGroup)) {
                backgroundColorID = R.color.accent
                foregroundColorID = R.color.black
            } else {
                foregroundColorID = if (isOutgoingMessage) {
                    if (isLightMode) R.color.white else R.color.black
                } else {
                    if (isLightMode) R.color.black else R.color.accent
                }
            }
            val backgroundColor = ResourcesCompat.getColor(context.resources, backgroundColorID, context.theme)
            result.setSpan(BackgroundColorSpan(backgroundColor), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val foregroundColor = ResourcesCompat.getColor(context.resources, foregroundColorID, context.theme)
            result.setSpan(ForegroundColorSpan(foregroundColor), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(StyleSpan(Typeface.BOLD), mention.first.lower, mention.first.upper, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    private fun isYou(mentionedPublicKey: String, userPublicKey: String, openGroup: OpenGroup?): Boolean {
        val isUserBlindedPublicKey = openGroup?.let { SodiumUtilities.sessionId(userPublicKey, mentionedPublicKey, it.publicKey) } ?: false
        return mentionedPublicKey.equals(userPublicKey, ignoreCase = true) || isUserBlindedPublicKey
    }
}