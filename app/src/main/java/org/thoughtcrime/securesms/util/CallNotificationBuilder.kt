package org.thoughtcrime.securesms.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.calls.WebRtcCallActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.preferences.SettingsActivity
import org.thoughtcrime.securesms.service.WebRtcCallService
import org.thoughtcrime.securesms.ui.getSubbedCharSequence
import org.thoughtcrime.securesms.ui.getSubbedString

class CallNotificationBuilder {

    companion object {
        const val WEBRTC_NOTIFICATION = 313388

        const val TYPE_INCOMING_RINGING = 1
        const val TYPE_OUTGOING_RINGING = 2
        const val TYPE_ESTABLISHED = 3
        const val TYPE_INCOMING_CONNECTING = 4
        const val TYPE_INCOMING_PRE_OFFER = 5

        @JvmStatic
        fun areNotificationsEnabled(context: Context): Boolean {
            val notificationManager = NotificationManagerCompat.from(context)
            return notificationManager.areNotificationsEnabled()
        }

        @JvmStatic
        fun getFirstCallNotification(context: Context, callerName: String): Notification {
            val contentIntent = Intent(context, SettingsActivity::class.java)

            val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val titleTxt = context.getSubbedString(R.string.callsMissedCallFrom, NAME_KEY to callerName)
            val bodyTxt = context.getSubbedCharSequence(
                R.string.callsYouMissedCallPermissions,
                NAME_KEY to callerName
            )

            val builder = NotificationCompat.Builder(context, NotificationChannels.CALLS)
                    .setSound(null)
                    .setSmallIcon(R.drawable.ic_baseline_call_24)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentTitle(titleTxt)
                    .setContentText(bodyTxt)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bodyTxt))
                    .setAutoCancel(true)

            return builder.build()
        }

        @JvmStatic
        fun getCallInProgressNotification(context: Context, type: Int, recipient: Recipient?): Notification {
            val contentIntent = Intent(context, WebRtcCallActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

            val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val builder = NotificationCompat.Builder(context, NotificationChannels.CALLS)
                    .setSound(null)
                    .setSmallIcon(R.drawable.ic_baseline_call_24)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)

            var recipName = "Unknown"
            recipient?.name?.let { name ->
                builder.setContentTitle(name)
                recipName = name
            }

            when (type) {
                TYPE_INCOMING_CONNECTING -> {
                    builder.setContentText(context.getString(R.string.callsConnecting))
                            .setNotificationSilent()
                }
                TYPE_INCOMING_PRE_OFFER,
                TYPE_INCOMING_RINGING -> {
                    val txt = Phrase.from(context, R.string.callsIncoming).put(NAME_KEY, recipName).format()
                    builder.setContentText(txt)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                    builder.addAction(getServiceNotificationAction(
                            context,
                            WebRtcCallService.ACTION_DENY_CALL,
                            R.drawable.ic_close_grey600_32dp,
                            R.string.decline
                    ))
                    // If notifications aren't enabled, we will trigger the intent from WebRtcCallService
                    builder.setFullScreenIntent(getFullScreenPendingIntent(
                        context
                    ), true)
                    builder.addAction(getActivityNotificationAction(
                            context,
                            if (type == TYPE_INCOMING_PRE_OFFER) WebRtcCallActivity.ACTION_PRE_OFFER else WebRtcCallActivity.ACTION_ANSWER,
                            R.drawable.ic_phone_grey600_32dp,
                            R.string.accept
                    ))
                    builder.priority = NotificationCompat.PRIORITY_MAX
                }
                TYPE_OUTGOING_RINGING -> {
                    builder.setContentText(context.getString(R.string.callsConnecting))
                    builder.addAction(getServiceNotificationAction(
                            context,
                            WebRtcCallService.ACTION_LOCAL_HANGUP,
                            R.drawable.ic_call_end_grey600_32dp,
                            R.string.cancel
                    ))
                }
                else -> {
                    builder.setContentText(context.getString(R.string.callsInProgress))
                    builder.addAction(getServiceNotificationAction(
                            context,
                            WebRtcCallService.ACTION_LOCAL_HANGUP,
                            R.drawable.ic_call_end_grey600_32dp,
                            R.string.callsEnd
                    )).setUsesChronometer(true)
                }
            }

            return builder.build()
        }

        private fun getServiceNotificationAction(context: Context, action: String, iconResId: Int, titleResId: Int): NotificationCompat.Action {
            val intent = Intent(context, WebRtcCallService::class.java)
                    .setAction(action)

            val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            return NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent)
        }

        private fun getFullScreenPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WebRtcCallActivity::class.java)
                .setFlags(FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                .setAction(WebRtcCallActivity.ACTION_FULL_SCREEN_INTENT)

            return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun getActivityNotificationAction(context: Context, action: String,
                                                  @DrawableRes iconResId: Int, @StringRes titleResId: Int): NotificationCompat.Action {
            val intent = Intent(context, WebRtcCallActivity::class.java)
                    .setAction(action)

            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            return NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent)
        }

    }
}