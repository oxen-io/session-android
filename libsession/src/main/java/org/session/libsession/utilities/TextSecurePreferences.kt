package org.session.libsession.utilities

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.hardware.Camera
import android.net.Uri
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StyleRes
import androidx.core.app.NotificationCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.session.libsession.R
import org.session.libsession.utilities.TextSecurePreferences.Companion.instance
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preference definition.
 */
class Pref<T>(
    val name: String,
    val default: T,
    private val get: SharedPreferences.(String, T) -> T,
    private val set: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
) {
    fun get(prefs: SharedPreferences) = prefs.get(name, default)
    fun get(prefs: SharedPreferences, default: T) = prefs.get(name, default)
    fun set(value: T?, prefs: SharedPreferences) = prefs.edit().apply { value?.let { set(name, it) } ?: run { remove(name)} }.apply()
}

fun Pref(name: String, default: Boolean) = Pref(name, default, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)
fun Pref(name: String, default: Int) = Pref(name, default, SharedPreferences::getInt, SharedPreferences.Editor::putInt)
fun Pref(name: String, default: Long) = Pref(name, default, SharedPreferences::getLong, SharedPreferences.Editor::putLong)
fun Pref(name: String, default: String) = Pref(name, default, { _, _ -> getString(name, null) ?: default }, SharedPreferences.Editor::putStringOrRemove)
fun NullablePref(name: String, default: String?) = Pref(name, default, { _, _ -> getString(name, null) ?: default }, SharedPreferences.Editor::putStringOrRemove)
fun Pref(name: String) = Pref(name, null, SharedPreferences::getString, SharedPreferences.Editor::putStringOrRemove)
fun Pref(name: String, default: Set<String>) = Pref(name, default, SharedPreferences::getStringSet, SharedPreferences.Editor::putStringSet)

private fun SharedPreferences.Editor.putStringOrRemove(name: String, value: String?) = value?.let { putString(name, it) } ?: run { remove(name) }

operator fun <T> SharedPreferences.get(pref: Pref<T>): T = pref.get(this)
operator fun <T> SharedPreferences.get(pref: Pref<T>, default: T): T = pref.get(this, default)
operator fun <T> SharedPreferences.set(pref: Pref<T>, value: T?) = pref.set(value, this)

val Context.prefs get() = instance ?: TextSecurePreferences(this).also { instance = it }

@Singleton
class TextSecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val Context.prefs get() = TextSecurePreferences(this)

    private val sharedPreferences = getDefaultSharedPreferences(context)

    operator fun <T> get(pref: Pref<T>): T = sharedPreferences[pref]

    val LED_COLOR_PREF_PRIMARY = Pref("pref_led_color_primary", ThemeUtil.getThemedColor(context, R.attr.colorAccent))

    fun StringSetPref(name: String, @ArrayRes defaultValuesRes: Int): Pref<Set<String>?> {
        return Pref(name, context.resources.getStringArray(defaultValuesRes).toSet())
    }

    val MEDIA_DOWNLOAD_MOBILE_PREF = StringSetPref("pref_media_download_mobile", R.array.pref_media_download_mobile_data_default)
    val MEDIA_DOWNLOAD_WIFI_PREF = StringSetPref("pref_media_download_wifi", R.array.pref_media_download_wifi_default)
    val MEDIA_DOWNLOAD_ROAMING_PREF = StringSetPref("pref_media_download_roaming", R.array.pref_media_download_roaming_default)

    companion object {
        val TAG = TextSecurePreferences::class.simpleName

        var instance: TextSecurePreferences? = null

        @JvmStatic
        var pushSuffix = ""

        val DISABLE_PASSPHRASE_PREF = Pref("pref_disable_passphrase", true)
        val LANGUAGE_PREF = Pref("pref_language", "zz")
        val LAST_VERSION_CODE_PREF = Pref("last_version_code", 0)
        val RINGTONE_PREF = NullablePref("pref_key_ringtone", Settings.System.DEFAULT_NOTIFICATION_URI?.toString())
        val VIBRATE_PREF = Pref("pref_key_vibrate", true)
        val NOTIFICATION_PREF = Pref("pref_key_enable_notifications", true)
        val PASSPHRASE_TIMEOUT_INTERVAL_PREF = Pref("pref_timeout_interval", 5 * 60)
        val PASSPHRASE_TIMEOUT_PREF = Pref("pref_timeout_passphrase", false)
        val SCREEN_SECURITY_PREF = Pref("pref_screen_security", true)
        val ENTER_SENDS_PREF = Pref("pref_enter_sends", false)
        val THREAD_TRIM_ENABLED = Pref("pref_trim_threads", true)
        val LOCAL_NUMBER_PREF = Pref("pref_local_number")
        val LOCAL_REGISTRATION_ID_PREF = Pref("pref_local_registration_id", 0)
        val REPEAT_ALERTS_PREF = Pref("pref_repeat_alerts")
        val NOTIFICATION_PRIVACY_PREF = Pref("pref_notification_privacy", "all")
        val NOTIFICATION_PRIORITY_PREF = Pref("pref_notification_priority", NotificationCompat.PRIORITY_HIGH.toString())
        val SYSTEM_EMOJI_PREF = Pref("pref_system_emoji", false)
        val DIRECT_CAPTURE_CAMERA_ID = Pref("pref_direct_capture_camera_id", Camera.CameraInfo.CAMERA_FACING_BACK)
        val PROFILE_KEY_PREF = Pref("pref_profile_key")
        val PROFILE_NAME_PREF = Pref("pref_profile_name")
        val PROFILE_AVATAR_ID_PREF = Pref("pref_profile_avatar_id", 0)
        val PROFILE_AVATAR_URL_PREF = Pref("pref_profile_avatar_url")
        val READ_RECEIPTS_PREF = Pref("pref_read_receipts", false)
        val INCOGNITO_KEYBORAD_PREF = Pref("pref_incognito_keyboard", true)
        val DATABASE_ENCRYPTED_SECRET = Pref("pref_database_encrypted_secret")
        val DATABASE_UNENCRYPTED_SECRET = Pref("pref_database_unencrypted_secret")
        val ATTACHMENT_ENCRYPTED_SECRET = Pref("pref_attachment_encrypted_secret")
        val ATTACHMENT_UNENCRYPTED_SECRET = Pref("pref_attachment_unencrypted_secret")
        val SCREEN_LOCK = Pref("pref_android_screen_lock", false)
        val SCREEN_LOCK_TIMEOUT = Pref("pref_android_screen_lock_timeout", 0L)
        val LOG_ENCRYPTED_SECRET = Pref("pref_log_encrypted_secret")
        val LOG_UNENCRYPTED_SECRET = Pref("pref_log_unencrypted_secret")
        val NOTIFICATION_CHANNEL_VERSION = Pref("pref_notification_channel_version", 1)
        val NOTIFICATION_MESSAGES_CHANNEL_VERSION = Pref("pref_notification_messages_channel_version", 1)
        val HAS_VIEWED_SEED = Pref("has_viewed_seed", false)
        val LAST_PROFILE_PICTURE_UPLOAD = Pref("last_profile_picture_upload", 0L)
        val LAST_SNODE_POOL_REFRESH_DATE = Pref("last_snode_pool_refresh_date", 0L)
        val TYPING_INDICATORS = Pref("pref_typing_indicators", false)
        val LINK_PREVIEWS = Pref("pref_link_previews", false)
        val GIF_METADATA_WARNING = Pref("has_seen_gif_metadata_warning", false)
        val GIF_GRID_LAYOUT = Pref("pref_gif_grid_layout", false)
        val IS_PUSH_ENABLED get() = Pref("pref_is_using_fcm$pushSuffix", false)
        val PUSH_TOKEN get() = Pref("pref_fcm_token_2$pushSuffix", "")
        val PUSH_REGISTER_TIME get() = Pref("pref_last_fcm_token_upload_time_2$pushSuffix", 0L)
        val CONFIGURATION_SYNCED = Pref("pref_configuration_synced", false)
        val LAST_PROFILE_UPDATE_TIME = Pref("pref_last_profile_update_time", 0L)
        val LAST_OPEN_DATE = Pref("pref_last_open_date", 0L)
        val HAS_SEEN_LINK_PREVIEW_SUGGESTION_DIALOG = Pref("has_seen_link_preview_suggestion_dialog", false)
        val HAS_HIDDEN_MESSAGE_REQUESTS = Pref("pref_message_requests_hidden", false)
        val CALL_NOTIFICATIONS_ENABLED = Pref("pref_call_notifications_enabled", false)
        val SHOWN_CALL_NOTIFICATION = Pref("pref_shown_call_notification", false) // call notification is a prompt to check privacy settings
        val LAST_VACUUM_TIME = Pref("pref_last_vacuum_time", 0L)
        val AUTOPLAY_AUDIO_MESSAGES = Pref("pref_autoplay_audio", false)
        val FINGERPRINT_KEY_GENERATED = Pref("fingerprint_key_generated", false)
        val SELECTED_ACCENT_COLOR = Pref("selected_accent_color")

        val HAS_RECEIVED_LEGACY_CONFIG = Pref("has_received_legacy_config", false)

        val PATCH_SNODE_VERSION_2024_07_23 = Pref("libsession.patch_snode_version_2024_07_23", false)

        const val GREEN_ACCENT = "accent_green"
        const val BLUE_ACCENT = "accent_blue"
        const val PURPLE_ACCENT = "accent_purple"
        const val PINK_ACCENT = "accent_pink"
        const val RED_ACCENT = "accent_red"
        const val ORANGE_ACCENT = "accent_orange"
        const val YELLOW_ACCENT = "accent_yellow"

        const val CLASSIC_DARK = "classic.dark"
        const val CLASSIC_LIGHT = "classic.light"
        const val OCEAN_DARK = "ocean.dark"
        const val OCEAN_LIGHT = "ocean.light"

        val SELECTED_STYLE: Pref<String> = Pref("pref_selected_style", CLASSIC_DARK) // classic_dark/light, ocean_dark/light
        val FOLLOW_SYSTEM_SETTINGS = Pref("pref_follow_system", false) // follow system day/night
        val HIDE_PASSWORD = Pref("pref_hide_password", false)

        val LEGACY_PREF_KEY_SELECTED_UI_MODE = Pref("SELECTED_UI_MODE") // this will be cleared upon launching app, for users migrating to theming build

        const val ALLOW_MESSAGE_REQUESTS = "libsession.ALLOW_MESSAGE_REQUESTS"
    }

    fun <T> set(pref: Pref<T>, value: T?) = sharedPreferences.set(pref, value)
    fun <T> remove(pref: Pref<T>) = sharedPreferences.set(pref, null)
    fun <T> has(pref: Pref<T>) = sharedPreferences.contains(pref.name)
    fun <T> flow(pref: Pref<T>) = callbackFlow {
        OnSharedPreferenceChangeListener { _, _ -> trySend(sharedPreferences[pref]) }.let {
            trySend(sharedPreferences[pref])

            sharedPreferences.registerOnSharedPreferenceChangeListener(it)

            awaitClose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(it)
            }
        }
    }

    fun getConfigurationMessageSynced(): Boolean = sharedPreferences[CONFIGURATION_SYNCED]
    fun configurationMessageSyncedFlow() = flow(CONFIGURATION_SYNCED)
    fun setConfigurationMessageSynced(value: Boolean) = set(CONFIGURATION_SYNCED, value)
    fun isPushEnabled(): Boolean = sharedPreferences[IS_PUSH_ENABLED]
    fun setPushEnabled(value: Boolean) = sharedPreferences.set(IS_PUSH_ENABLED, value)
    fun getPushToken(): String? = sharedPreferences[PUSH_TOKEN]
    fun setPushToken(value: String) = set(PUSH_TOKEN, value)
    fun getPushRegisterTime(): Long = sharedPreferences[PUSH_REGISTER_TIME]
    fun setPushRegisterTime(value: Long) = set(PUSH_REGISTER_TIME, value)
    fun isScreenLockEnabled() = sharedPreferences[SCREEN_LOCK]
    fun setScreenLockEnabled(value: Boolean) = set(SCREEN_LOCK, value)
    fun getScreenLockTimeout(): Long = sharedPreferences[SCREEN_LOCK_TIMEOUT]
    fun setScreenLockTimeout(value: Long) = set(SCREEN_LOCK_TIMEOUT, value)
    fun setAttachmentEncryptedSecret(secret: String) = set(ATTACHMENT_ENCRYPTED_SECRET, secret)
    fun setAttachmentUnencryptedSecret(secret: String?) = set(ATTACHMENT_UNENCRYPTED_SECRET, secret)
    fun getAttachmentEncryptedSecret(): String? = sharedPreferences[ATTACHMENT_ENCRYPTED_SECRET]
    fun getAttachmentUnencryptedSecret(): String? = sharedPreferences[ATTACHMENT_UNENCRYPTED_SECRET]
    fun setDatabaseEncryptedSecret(secret: String) = set(DATABASE_ENCRYPTED_SECRET, secret)
    fun setDatabaseUnencryptedSecret(secret: String?) = set(DATABASE_UNENCRYPTED_SECRET, secret)
    fun getDatabaseUnencryptedSecret(): String? = sharedPreferences[DATABASE_UNENCRYPTED_SECRET]
    fun getDatabaseEncryptedSecret(): String? = sharedPreferences[DATABASE_ENCRYPTED_SECRET]
    fun isIncognitoKeyboardEnabled() = sharedPreferences[INCOGNITO_KEYBORAD_PREF]
    fun isReadReceiptsEnabled(): Boolean = sharedPreferences[READ_RECEIPTS_PREF]
    fun isTypingIndicatorsEnabled() = sharedPreferences[TYPING_INDICATORS]
    fun isLinkPreviewsEnabled(): Boolean = sharedPreferences[LINK_PREVIEWS]
    fun setLinkPreviewsEnabled(enabled: Boolean) = set(LINK_PREVIEWS, enabled)
    fun hasSeenGIFMetaDataWarning(): Boolean = sharedPreferences[GIF_METADATA_WARNING]
    fun setHasSeenGIFMetaDataWarning() = set(GIF_METADATA_WARNING, true)
    fun isGifSearchInGridLayout() = sharedPreferences[GIF_GRID_LAYOUT]
    fun setIsGifSearchInGridLayout(isGrid: Boolean) = set(GIF_GRID_LAYOUT, isGrid)
    fun getProfileKey(): String? = sharedPreferences[PROFILE_KEY_PREF]
    fun setProfileKey(key: String?) = set(PROFILE_KEY_PREF, key)
    fun setProfileName(name: String?) = sharedPreferences.set(PROFILE_NAME_PREF, name)
    fun getProfileName(): String? = sharedPreferences[PROFILE_NAME_PREF]
    fun profileNameFlow() = flow(PROFILE_NAME_PREF)
    fun setProfileAvatarId(id: Int) = set(PROFILE_AVATAR_ID_PREF, id)
    fun getProfileAvatarId(): Int = sharedPreferences[PROFILE_AVATAR_ID_PREF]
    fun setProfilePictureURL(url: String?) = set(PROFILE_AVATAR_URL_PREF, url)
    fun getProfilePictureURL(): String? = sharedPreferences[PROFILE_AVATAR_URL_PREF]
    fun getNotificationPriority(): Int = sharedPreferences[NOTIFICATION_PRIORITY_PREF]!!.toInt()
    fun setDirectCaptureCameraId(value: Int) = set(DIRECT_CAPTURE_CAMERA_ID, value)
    fun getDirectCaptureCameraId(): Int = sharedPreferences[DIRECT_CAPTURE_CAMERA_ID]
    fun getNotificationPrivacy(): NotificationPrivacyPreference = NotificationPrivacyPreference(sharedPreferences[NOTIFICATION_PRIVACY_PREF])
    fun getRepeatAlertsCount(): Int = runCatching { sharedPreferences[REPEAT_ALERTS_PREF] }.getOrNull()?.toInt() ?: 0
    fun getLocalRegistrationId(): Int = sharedPreferences[LOCAL_REGISTRATION_ID_PREF]
    fun setLocalRegistrationId(registrationId: Int) = set(LOCAL_REGISTRATION_ID_PREF, registrationId)
    fun getLocalNumber(): String? = sharedPreferences[LOCAL_NUMBER_PREF]
    fun getHasLegacyConfig(): Boolean = sharedPreferences[HAS_RECEIVED_LEGACY_CONFIG]
    fun hasLegacyConfigFlow(): Flow<Boolean> = flow(HAS_RECEIVED_LEGACY_CONFIG)
    fun setHasLegacyConfig(newValue: Boolean) = sharedPreferences.set(HAS_RECEIVED_LEGACY_CONFIG, newValue)
    fun setLocalNumber(localNumber: String) = set(LOCAL_NUMBER_PREF, localNumber.lowercase())
    fun isEnterSendsEnabled() = sharedPreferences[ENTER_SENDS_PREF]
    fun isPasswordDisabled(): Boolean = sharedPreferences[DISABLE_PASSPHRASE_PREF]
    fun setPasswordDisabled(disabled: Boolean) = set(DISABLE_PASSPHRASE_PREF, disabled)
    fun isScreenSecurityEnabled() = sharedPreferences[SCREEN_SECURITY_PREF]
    fun getLastVersionCode(): Int = sharedPreferences[LAST_VERSION_CODE_PREF]
    fun setLastVersionCode(versionCode: Int) = set(LAST_VERSION_CODE_PREF, versionCode)
    fun isPassphraseTimeoutEnabled() = sharedPreferences[PASSPHRASE_TIMEOUT_PREF]
    fun getPassphraseTimeoutInterval() = sharedPreferences[PASSPHRASE_TIMEOUT_INTERVAL_PREF]
    fun getLanguage(): String? = sharedPreferences[LANGUAGE_PREF]
    fun isNotificationsEnabled() = sharedPreferences[NOTIFICATION_PREF]
    fun getNotificationRingtone(): String? = sharedPreferences[RINGTONE_PREF]?.takeUnless { it.startsWith("file:") } ?: RINGTONE_PREF.default
    fun getNotificationRingtoneUri(): Uri = getNotificationRingtone().let(Uri::parse)
    fun removeNotificationRingtone() = remove(RINGTONE_PREF)
    fun setNotificationRingtone(ringtone: String?) = set(RINGTONE_PREF, ringtone)
    fun setNotificationVibrateEnabled(enabled: Boolean) = set(VIBRATE_PREF, enabled)
    fun isNotificationVibrateEnabled(): Boolean = sharedPreferences[VIBRATE_PREF]
    fun getNotificationLedColor(): Int = sharedPreferences[LED_COLOR_PREF_PRIMARY]
    fun isThreadLengthTrimmingEnabled() = sharedPreferences[THREAD_TRIM_ENABLED]
    fun isSystemEmojiPreferred() = sharedPreferences[SYSTEM_EMOJI_PREF]
    fun getMobileMediaDownloadAllowed(): Set<String>? = sharedPreferences[MEDIA_DOWNLOAD_MOBILE_PREF]
    fun getWifiMediaDownloadAllowed(): Set<String>? = sharedPreferences[MEDIA_DOWNLOAD_WIFI_PREF]
    fun getRoamingMediaDownloadAllowed(): Set<String>? = sharedPreferences[MEDIA_DOWNLOAD_ROAMING_PREF]
    fun getLogEncryptedSecret(): String? = sharedPreferences[LOG_ENCRYPTED_SECRET]
    fun setLogEncryptedSecret(base64Secret: String?) = set(LOG_ENCRYPTED_SECRET, base64Secret)
    fun getLogUnencryptedSecret(): String? = sharedPreferences[LOG_UNENCRYPTED_SECRET]
    fun getNotificationChannelVersion(): Int = sharedPreferences[NOTIFICATION_CHANNEL_VERSION]
    fun setNotificationChannelVersion(version: Int) = set(NOTIFICATION_CHANNEL_VERSION, version)
    fun getNotificationMessagesChannelVersion(): Int = sharedPreferences[NOTIFICATION_MESSAGES_CHANNEL_VERSION]
    fun setNotificationMessagesChannelVersion(version: Int) = set(NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    fun hasViewedSeed(): Boolean = sharedPreferences[HAS_VIEWED_SEED]
    fun setHasViewedSeed(hasViewedSeed: Boolean) = set(HAS_VIEWED_SEED, hasViewedSeed)
    fun getLastProfilePictureUpload(): Long = sharedPreferences[LAST_PROFILE_PICTURE_UPLOAD]
    fun setLastProfilePictureUpload(newValue: Long) = set(LAST_PROFILE_PICTURE_UPLOAD, newValue)
    fun getLastSnodePoolRefreshDate(): Long = sharedPreferences[LAST_SNODE_POOL_REFRESH_DATE]
    fun setLastSnodePoolRefreshDate(date: Date) = set(LAST_SNODE_POOL_REFRESH_DATE, date.time)
    fun shouldUpdateProfile(profileUpdateTime: Long): Boolean = profileUpdateTime > sharedPreferences[LAST_PROFILE_UPDATE_TIME]
    fun setLastProfileUpdateTime(profileUpdateTime: Long) = set(LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
    fun getLastOpenTimeDate(): Long = sharedPreferences[LAST_OPEN_DATE]
    fun setLastOpenDate() = set(LAST_OPEN_DATE, System.currentTimeMillis())
    fun hasSeenLinkPreviewSuggestionDialog(): Boolean = sharedPreferences[HAS_SEEN_LINK_PREVIEW_SUGGESTION_DIALOG]
    fun setHasSeenLinkPreviewSuggestionDialog() = set(HAS_SEEN_LINK_PREVIEW_SUGGESTION_DIALOG, true)
    fun isCallNotificationsEnabled(): Boolean = sharedPreferences[CALL_NOTIFICATIONS_ENABLED]
    fun setCallNotificationsEnabled(enabled: Boolean) = set(CALL_NOTIFICATIONS_ENABLED, enabled)
    fun getLastVacuumTime(): Long = sharedPreferences[LAST_VACUUM_TIME]
    fun setLastVacuumNow() = set(LAST_VACUUM_TIME, System.currentTimeMillis())
    fun setShownCallNotification(): Boolean = when {
        sharedPreferences[SHOWN_CALL_NOTIFICATION] -> false
        else -> {
            set(SHOWN_CALL_NOTIFICATION, true)
            true
        }
    }
    fun hasHiddenMessageRequests(): Boolean = sharedPreferences[HAS_HIDDEN_MESSAGE_REQUESTS]
    fun hasHiddenMessageRequestsFlow() = flow(HAS_HIDDEN_MESSAGE_REQUESTS)
    fun setHasHiddenMessageRequests() = set(HAS_HIDDEN_MESSAGE_REQUESTS, true)
    fun removeHasHiddenMessageRequests() = remove(HAS_HIDDEN_MESSAGE_REQUESTS)
    fun getFingerprintKeyGenerated(): Boolean = sharedPreferences[FINGERPRINT_KEY_GENERATED]
    fun setFingerprintKeyGenerated() = set(FINGERPRINT_KEY_GENERATED, true)

    fun hasSelectedAccentColor() = has(SELECTED_ACCENT_COLOR)
    fun getSelectedAccentColor(): String? = sharedPreferences[SELECTED_ACCENT_COLOR]

    var hasAppliedPatchSnodeVersion: Boolean
        get() = sharedPreferences[PATCH_SNODE_VERSION_2024_07_23]
        set(value) = set(PATCH_SNODE_VERSION_2024_07_23, value)

    @StyleRes
    fun getAccentColorStyle(): Int? = when (getSelectedAccentColor()) {
        GREEN_ACCENT -> R.style.PrimaryGreen
        BLUE_ACCENT -> R.style.PrimaryBlue
        PURPLE_ACCENT -> R.style.PrimaryPurple
        PINK_ACCENT -> R.style.PrimaryPink
        RED_ACCENT -> R.style.PrimaryRed
        ORANGE_ACCENT -> R.style.PrimaryOrange
        YELLOW_ACCENT -> R.style.PrimaryYellow
        else -> null
    }

    fun setAccentColorStyle(@StyleRes newColorStyle: Int?) = when (newColorStyle) {
        R.style.PrimaryGreen -> GREEN_ACCENT
        R.style.PrimaryBlue -> BLUE_ACCENT
        R.style.PrimaryPurple -> PURPLE_ACCENT
        R.style.PrimaryPink -> PINK_ACCENT
        R.style.PrimaryRed -> RED_ACCENT
        R.style.PrimaryOrange -> ORANGE_ACCENT
        R.style.PrimaryYellow -> YELLOW_ACCENT
        else -> null
    }.let { set(SELECTED_ACCENT_COLOR, it) }

    fun getThemeStyle(): String {
        migrateLegacyUiPrefIfNecessary()
        return sharedPreferences[SELECTED_STYLE]
    }

    fun setThemeStyle(themeStyle: String) {
        sharedPreferences[SELECTED_STYLE] = if (themeStyle !in listOf(CLASSIC_DARK, CLASSIC_LIGHT, OCEAN_DARK, OCEAN_LIGHT)) CLASSIC_DARK else themeStyle
    }

    fun getFollowSystemSettings(): Boolean {
        migrateLegacyUiPrefIfNecessary()
        return sharedPreferences[FOLLOW_SYSTEM_SETTINGS]
    }

    private fun migrateLegacyUiPrefIfNecessary() {
        val legacy = sharedPreferences[LEGACY_PREF_KEY_SELECTED_UI_MODE] ?: return
        val (mode, followSystem) = when (legacy) {
            "DAY" -> CLASSIC_LIGHT to false
            "NIGHT" -> CLASSIC_DARK to false
            "SYSTEM_DEFAULT" -> CLASSIC_DARK to true
            else -> CLASSIC_DARK to false
        }
        if (!has(FOLLOW_SYSTEM_SETTINGS) && !has(SELECTED_STYLE)) {
            setThemeStyle(mode)
            setFollowSystemSettings(followSystem)
        }
        remove(LEGACY_PREF_KEY_SELECTED_UI_MODE)
    }

    fun setFollowSystemSettings(followSystemSettings: Boolean) = set(FOLLOW_SYSTEM_SETTINGS, followSystemSettings)
    fun autoplayAudioMessages(): Boolean = sharedPreferences[AUTOPLAY_AUDIO_MESSAGES]
    fun clearAll() = getDefaultSharedPreferences(context).edit().clear().commit()
    fun getHidePassword() = sharedPreferences[HIDE_PASSWORD]
    fun setHidePassword(value: Boolean) = set(HIDE_PASSWORD, value)
}

fun <P : Preference?> PreferenceFragmentCompat.findPreference(pref: Pref<*>) = findPreference<P>(pref.name)
