package org.session.libsession.utilities

import android.content.Context
import android.hardware.Camera
import android.net.Uri
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StyleRes
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.session.libsession.R
import org.session.libsignal.utilities.Log
import java.io.IOException
import java.util.Arrays
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextSecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val TAG = TextSecurePreferences::class.simpleName

        internal val _events = MutableSharedFlow<String>(0, 64, BufferOverflow.DROP_OLDEST)
        val events get() = _events.asSharedFlow()

        @JvmStatic
        var pushSuffix = ""

        const val DISABLE_PASSPHRASE_PREF = "pref_disable_passphrase"
        const val LANGUAGE_PREF = "pref_language"
        const val LAST_VERSION_CODE_PREF = "last_version_code"
        const val RINGTONE_PREF = "pref_key_ringtone"
        const val VIBRATE_PREF = "pref_key_vibrate"
        const val NOTIFICATION_PREF = "pref_key_enable_notifications"
        const val LED_COLOR_PREF_PRIMARY = "pref_led_color_primary"
        const val PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval"
        const val PASSPHRASE_TIMEOUT_PREF = "pref_timeout_passphrase"
        const val SCREEN_SECURITY_PREF = "pref_screen_security"
        const val ENTER_SENDS_PREF = "pref_enter_sends"
        const val THREAD_TRIM_ENABLED = "pref_trim_threads"
        const val LOCAL_NUMBER_PREF = "pref_local_number"
        const val UPDATE_APK_REFRESH_TIME_PREF = "pref_update_apk_refresh_time"
        const val UPDATE_APK_DOWNLOAD_ID = "pref_update_apk_download_id"
        const val UPDATE_APK_DIGEST = "pref_update_apk_digest"
        const val IN_THREAD_NOTIFICATION_PREF = "pref_key_inthread_notifications"
        const val MESSAGE_BODY_TEXT_SIZE_PREF = "pref_message_body_text_size"
        const val LOCAL_REGISTRATION_ID_PREF = "pref_local_registration_id"
        const val REPEAT_ALERTS_PREF = "pref_repeat_alerts"
        const val NOTIFICATION_PRIVACY_PREF = "pref_notification_privacy"
        const val NOTIFICATION_PRIORITY_PREF = "pref_notification_priority"
        const val MEDIA_DOWNLOAD_MOBILE_PREF = "pref_media_download_mobile"
        const val MEDIA_DOWNLOAD_WIFI_PREF = "pref_media_download_wifi"
        const val MEDIA_DOWNLOAD_ROAMING_PREF = "pref_media_download_roaming"
        const val SYSTEM_EMOJI_PREF = "pref_system_emoji"
        const val DIRECT_CAPTURE_CAMERA_ID = "pref_direct_capture_camera_id"
        const val PROFILE_KEY_PREF = "pref_profile_key"
        const val PROFILE_NAME_PREF = "pref_profile_name"
        const val PROFILE_AVATAR_ID_PREF = "pref_profile_avatar_id"
        const val PROFILE_AVATAR_URL_PREF = "pref_profile_avatar_url"
        const val READ_RECEIPTS_PREF = "pref_read_receipts"
        const val INCOGNITO_KEYBORAD_PREF = "pref_incognito_keyboard"
        const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
        const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
        const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
        const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
        const val NEEDS_SQLCIPHER_MIGRATION = "pref_needs_sql_cipher_migration"
        const val BACKUP_ENABLED = "pref_backup_enabled_v3"
        const val BACKUP_PASSPHRASE = "pref_backup_passphrase"
        const val ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase"
        const val BACKUP_TIME = "pref_backup_next_time"
        const val BACKUP_SAVE_DIR = "pref_save_dir"
        const val SCREEN_LOCK = "pref_android_screen_lock"
        const val SCREEN_LOCK_TIMEOUT = "pref_android_screen_lock_timeout"
        const val LOG_ENCRYPTED_SECRET = "pref_log_encrypted_secret"
        const val LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret"
        const val NOTIFICATION_CHANNEL_VERSION = "pref_notification_channel_version"
        const val NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version"
        const val UNIVERSAL_UNIDENTIFIED_ACCESS = "pref_universal_unidentified_access"
        const val TYPING_INDICATORS = "pref_typing_indicators"
        const val LINK_PREVIEWS = "pref_link_previews"
        const val GIF_METADATA_WARNING = "has_seen_gif_metadata_warning"
        const val GIF_GRID_LAYOUT = "pref_gif_grid_layout"
        val IS_PUSH_ENABLED get() = "pref_is_using_fcm$pushSuffix"
        val PUSH_TOKEN get() = "pref_fcm_token_2$pushSuffix"
        val PUSH_REGISTER_TIME get() = "pref_last_fcm_token_upload_time_2$pushSuffix"
        const val LAST_CONFIGURATION_SYNC_TIME = "pref_last_configuration_sync_time"
        const val CONFIGURATION_SYNCED = "pref_configuration_synced"
        const val LAST_PROFILE_UPDATE_TIME = "pref_last_profile_update_time"
        const val LAST_OPEN_DATE = "pref_last_open_date"
        const val HAS_HIDDEN_MESSAGE_REQUESTS = "pref_message_requests_hidden"
        const val CALL_NOTIFICATIONS_ENABLED = "pref_call_notifications_enabled"
        const val SHOWN_CALL_WARNING = "pref_shown_call_warning" // call warning is user-facing warning of enabling calls
        const val SHOWN_CALL_NOTIFICATION = "pref_shown_call_notification" // call notification is a prompt to check privacy settings
        const val LAST_VACUUM_TIME = "pref_last_vacuum_time"
        const val AUTOPLAY_AUDIO_MESSAGES = "pref_autoplay_audio"
        const val FINGERPRINT_KEY_GENERATED = "fingerprint_key_generated"
        const val SELECTED_ACCENT_COLOR = "selected_accent_color"

        const val HAS_RECEIVED_LEGACY_CONFIG = "has_received_legacy_config"
        const val HAS_FORCED_NEW_CONFIG = "has_forced_new_config"

        const val GREEN_ACCENT = "accent_green"
        const val BLUE_ACCENT = "accent_blue"
        const val PURPLE_ACCENT = "accent_purple"
        const val PINK_ACCENT = "accent_pink"
        const val RED_ACCENT = "accent_red"
        const val ORANGE_ACCENT = "accent_orange"
        const val YELLOW_ACCENT = "accent_yellow"

        const val SELECTED_STYLE = "pref_selected_style" // classic_dark/light, ocean_dark/light
        const val FOLLOW_SYSTEM_SETTINGS = "pref_follow_system" // follow system day/night
        const val HIDE_PASSWORD = "pref_hide_password"

        const val LEGACY_PREF_KEY_SELECTED_UI_MODE = "SELECTED_UI_MODE" // this will be cleared upon launching app, for users migrating to theming build
        const val CLASSIC_DARK = "classic.dark"
        const val CLASSIC_LIGHT = "classic.light"
        const val OCEAN_DARK = "ocean.dark"
        const val OCEAN_LIGHT = "ocean.light"

        const val ALLOW_MESSAGE_REQUESTS = "libsession.ALLOW_MESSAGE_REQUESTS"

        @JvmStatic
        fun getConfigurationMessageSynced(context: Context): Boolean {
            return getBooleanPreference(context, CONFIGURATION_SYNCED, false)
        }

        @JvmStatic
        fun setConfigurationMessageSynced(context: Context, value: Boolean) {
            setBooleanPreference(context, CONFIGURATION_SYNCED, value)
            _events.tryEmit(CONFIGURATION_SYNCED)
        }

        @JvmStatic
        fun isPushEnabled(context: Context): Boolean {
            return getBooleanPreference(context, IS_PUSH_ENABLED, false)
        }

        @JvmStatic
        fun getPushToken(context: Context): String? {
            return getStringPreference(context, PUSH_TOKEN, "")
        }

        fun getPushRegisterTime(context: Context): Long {
            return getLongPreference(context, PUSH_REGISTER_TIME, 0)
        }

        fun setPushRegisterTime(context: Context, value: Long) {
            setLongPreference(context, PUSH_REGISTER_TIME, value)
        }

        // endregion
        @JvmStatic
        fun isScreenLockEnabled(context: Context): Boolean {
            return getBooleanPreference(context, SCREEN_LOCK, false)
        }

        @JvmStatic
        fun setScreenLockEnabled(context: Context, value: Boolean) {
            setBooleanPreference(context, SCREEN_LOCK, value)
        }

        @JvmStatic
        fun getScreenLockTimeout(context: Context): Long {
            return getLongPreference(context, SCREEN_LOCK_TIMEOUT, 0)
        }

        @JvmStatic
        fun setScreenLockTimeout(context: Context, value: Long) {
            setLongPreference(context, SCREEN_LOCK_TIMEOUT, value)
        }

        @JvmStatic
        fun setAttachmentEncryptedSecret(context: Context, secret: String) {
            setStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
            setStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun getAttachmentEncryptedSecret(context: Context): String? {
            return getStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun getAttachmentUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setDatabaseEncryptedSecret(context: Context, secret: String) {
            setStringPreference(context, DATABASE_ENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun setDatabaseUnencryptedSecret(context: Context, secret: String?) {
            setStringPreference(context, DATABASE_UNENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun getDatabaseUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun getDatabaseEncryptedSecret(context: Context): String? {
            return getStringPreference(context, DATABASE_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun isIncognitoKeyboardEnabled(context: Context): Boolean {
            return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, true)
        }

        @JvmStatic
        fun isReadReceiptsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, READ_RECEIPTS_PREF, false)
        }

        fun setReadReceiptsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, READ_RECEIPTS_PREF, enabled)
        }

        @JvmStatic
        fun isTypingIndicatorsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, TYPING_INDICATORS, false)
        }

        @JvmStatic
        fun setTypingIndicatorsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, TYPING_INDICATORS, enabled)
        }

        @JvmStatic
        fun isLinkPreviewsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, LINK_PREVIEWS, false)
        }

        @JvmStatic
        fun setLinkPreviewsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, LINK_PREVIEWS, enabled)
        }

        @JvmStatic
        fun hasSeenGIFMetaDataWarning(context: Context): Boolean {
            return getBooleanPreference(context, GIF_METADATA_WARNING, false)
        }

        @JvmStatic
        fun setHasSeenGIFMetaDataWarning(context: Context) {
            setBooleanPreference(context, GIF_METADATA_WARNING, true)
        }

        @JvmStatic
        fun isGifSearchInGridLayout(context: Context): Boolean {
            return getBooleanPreference(context, GIF_GRID_LAYOUT, false)
        }

        @JvmStatic
        fun setIsGifSearchInGridLayout(context: Context, isGrid: Boolean) {
            setBooleanPreference(context, GIF_GRID_LAYOUT, isGrid)
        }

        @JvmStatic
        fun getProfileKey(context: Context): String? {
            return getStringPreference(context, PROFILE_KEY_PREF, null)
        }

        @JvmStatic
        fun setProfileKey(context: Context, key: String?) {
            setStringPreference(context, PROFILE_KEY_PREF, key)
        }

        @JvmStatic
        fun setProfileName(context: Context, name: String?) {
            setStringPreference(context, PROFILE_NAME_PREF, name)
            _events.tryEmit(PROFILE_NAME_PREF)
        }

        @JvmStatic
        fun getProfileName(context: Context): String? {
            return getStringPreference(context, PROFILE_NAME_PREF, null)
        }

        @JvmStatic
        fun setProfileAvatarId(context: Context, id: Int) {
            setIntegerPreference(context, PROFILE_AVATAR_ID_PREF, id)
        }

        @JvmStatic
        fun getProfileAvatarId(context: Context): Int {
            return getIntegerPreference(context, PROFILE_AVATAR_ID_PREF, 0)
        }

        fun setProfilePictureURL(context: Context, url: String?) {
            setStringPreference(context, PROFILE_AVATAR_URL_PREF, url)
        }

        @JvmStatic
        fun getProfilePictureURL(context: Context): String? {
            return getStringPreference(context, PROFILE_AVATAR_URL_PREF, null)
        }

        @JvmStatic
        fun getNotificationPriority(context: Context): Int {
            return getStringPreference(context, NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
        }

        @JvmStatic
        fun getMessageBodyTextSize(context: Context): Int {
            return getStringPreference(context, MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
        }

        @JvmStatic
        fun setDirectCaptureCameraId(context: Context, value: Int) {
            setIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, value)
        }

        @JvmStatic
        fun getDirectCaptureCameraId(context: Context): Int {
            return getIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK)
        }

        @JvmStatic
        fun getNotificationPrivacy(context: Context): NotificationPrivacyPreference {
            return NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"))
        }

        @JvmStatic
        fun getRepeatAlertsCount(context: Context): Int {
            return try {
                getStringPreference(context, REPEAT_ALERTS_PREF, "0")!!.toInt()
            } catch (e: NumberFormatException) {
                Log.w(TAG, e)
                0
            }
        }

        fun getLocalRegistrationId(context: Context): Int {
            return getIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, 0)
        }

        fun setLocalRegistrationId(context: Context, registrationId: Int) {
            setIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, registrationId)
        }

        @JvmStatic
        fun getUpdateApkDownloadId(context: Context): Long {
            return getLongPreference(context, UPDATE_APK_DOWNLOAD_ID, -1)
        }

        @JvmStatic
        fun getUpdateApkDigest(context: Context): String? {
            return getStringPreference(context, UPDATE_APK_DIGEST, null)
        }

        @JvmStatic
        fun getLocalNumber(context: Context): String? {
            return getStringPreference(context, LOCAL_NUMBER_PREF, null)
        }

        @JvmStatic
        fun setHasLegacyConfig(context: Context, newValue: Boolean) {
            setBooleanPreference(context, HAS_RECEIVED_LEGACY_CONFIG, newValue)
            _events.tryEmit(HAS_RECEIVED_LEGACY_CONFIG)
        }

        fun setLocalNumber(context: Context, localNumber: String) {
            setStringPreference(context, LOCAL_NUMBER_PREF, localNumber.toLowerCase())
        }

        @JvmStatic
        fun isEnterSendsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, ENTER_SENDS_PREF, false)
        }

        @JvmStatic
        fun isPasswordDisabled(context: Context): Boolean {
            return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, true)
        }

        fun setPasswordDisabled(context: Context, disabled: Boolean) {
            setBooleanPreference(context, DISABLE_PASSPHRASE_PREF, disabled)
        }

        @JvmStatic
        fun isScreenSecurityEnabled(context: Context): Boolean {
            return getBooleanPreference(context, SCREEN_SECURITY_PREF, context.resources.getBoolean(R.bool.screen_security_default))
        }

        fun getLastVersionCode(context: Context): Int {
            return getIntegerPreference(context, LAST_VERSION_CODE_PREF, 0)
        }

        @Throws(IOException::class)
        fun setLastVersionCode(context: Context, versionCode: Int) {
            if (!setIntegerPreferenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
                throw IOException("couldn't write version code to sharedpreferences")
            }
        }

        @JvmStatic
        fun isPassphraseTimeoutEnabled(context: Context): Boolean {
            return getBooleanPreference(context, PASSPHRASE_TIMEOUT_PREF, false)
        }

        @JvmStatic
        fun getPassphraseTimeoutInterval(context: Context): Int {
            return getIntegerPreference(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
        }

        @JvmStatic
        fun getLanguage(context: Context): String? {
            return getStringPreference(context, LANGUAGE_PREF, "zz")
        }

        @JvmStatic
        fun isNotificationsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, NOTIFICATION_PREF, true)
        }

        @JvmStatic
        fun getNotificationRingtone(context: Context): Uri {
            var result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
            if (result != null && result.startsWith("file:")) {
                result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
            }
            return Uri.parse(result)
        }

        @JvmStatic
        fun isNotificationVibrateEnabled(context: Context): Boolean {
            return getBooleanPreference(context, VIBRATE_PREF, true)
        }

        @JvmStatic
        fun getNotificationLedColor(context: Context): Int {
            return getIntegerPreference(context, LED_COLOR_PREF_PRIMARY, ThemeUtil.getThemedColor(context, R.attr.colorAccent))
        }

        @JvmStatic
        fun isThreadLengthTrimmingEnabled(context: Context): Boolean {
            return getBooleanPreference(context, THREAD_TRIM_ENABLED, true)
        }

        @JvmStatic
        fun isSystemEmojiPreferred(context: Context): Boolean {
            return getBooleanPreference(context, SYSTEM_EMOJI_PREF, false)
        }

        @JvmStatic
        fun getMobileMediaDownloadAllowed(context: Context): Set<String>? {
            return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default)
        }

        @JvmStatic
        fun getWifiMediaDownloadAllowed(context: Context): Set<String>? {
            return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default)
        }

        @JvmStatic
        fun getRoamingMediaDownloadAllowed(context: Context): Set<String>? {
            return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default)
        }

        private fun getMediaDownloadAllowed(context: Context, key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
            return getStringSetPreference(context, key, HashSet(Arrays.asList(*context.resources.getStringArray(defaultValuesRes))))
        }

        @JvmStatic
        fun getLogEncryptedSecret(context: Context): String? {
            return getStringPreference(context, LOG_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setLogEncryptedSecret(context: Context, base64Secret: String?) {
            setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret)
        }

        @JvmStatic
        fun getLogUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setLogUnencryptedSecret(context: Context, base64Secret: String?) {
            setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret)
        }

        @JvmStatic
        fun getNotificationChannelVersion(context: Context): Int {
            return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1)
        }

        @JvmStatic
        fun setNotificationChannelVersion(context: Context, version: Int) {
            setIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, version)
        }

        @JvmStatic
        fun getNotificationMessagesChannelVersion(context: Context): Int {
            return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
        }

        @JvmStatic
        fun setNotificationMessagesChannelVersion(context: Context, version: Int) {
            setIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
        }

        @JvmStatic
        fun hasForcedNewConfig(context: Context): Boolean {
            return getBooleanPreference(context, HAS_FORCED_NEW_CONFIG, false)
        }

        @JvmStatic
        fun getBooleanPreference(context: Context, key: String?, defaultValue: Boolean): Boolean {
            return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
        }

        @JvmStatic
        fun setBooleanPreference(context: Context, key: String?, value: Boolean) {
            getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
        }

        @JvmStatic
        fun getStringPreference(context: Context, key: String, defaultValue: String?): String? {
            return getDefaultSharedPreferences(context).getString(key, defaultValue)
        }

        @JvmStatic
        fun setStringPreference(context: Context, key: String?, value: String?) {
            getDefaultSharedPreferences(context).edit().putString(key, value).apply()
        }

        fun getIntegerPreference(context: Context, key: String, defaultValue: Int): Int {
            return getDefaultSharedPreferences(context).getInt(key, defaultValue)
        }

        private fun setIntegerPreference(context: Context, key: String, value: Int) {
            getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
        }

        private fun setIntegerPreferenceBlocking(context: Context, key: String, value: Int): Boolean {
            return getDefaultSharedPreferences(context).edit().putInt(key, value).commit()
        }

        private fun getLongPreference(context: Context, key: String, defaultValue: Long): Long {
            return getDefaultSharedPreferences(context).getLong(key, defaultValue)
        }

        private fun setLongPreference(context: Context, key: String, value: Long) {
            getDefaultSharedPreferences(context).edit().putLong(key, value).apply()
        }

        private fun removePreference(context: Context, key: String) {
            getDefaultSharedPreferences(context).edit().remove(key).apply()
        }

        private fun getStringSetPreference(context: Context, key: String, defaultValues: Set<String>): Set<String>? {
            val prefs = getDefaultSharedPreferences(context)
            return if (prefs.contains(key)) {
                prefs.getStringSet(key, emptySet())
            } else {
                defaultValues
            }
        }

        fun getHasViewedSeed(context: Context): Boolean {
            return getBooleanPreference(context, "has_viewed_seed", false)
        }

        fun setHasViewedSeed(context: Context, hasViewedSeed: Boolean) {
            setBooleanPreference(context, "has_viewed_seed", hasViewedSeed)
        }

        fun setRestorationTime(context: Context, time: Long) {
            setLongPreference(context, "restoration_time", time)
        }

        @JvmStatic
        fun getLastProfilePictureUpload(context: Context): Long {
            return getLongPreference(context, "last_profile_picture_upload", 0)
        }

        @JvmStatic
        fun setLastProfilePictureUpload(context: Context, newValue: Long) {
            setLongPreference(context, "last_profile_picture_upload", newValue)
        }

        fun getLastSnodePoolRefreshDate(context: Context?): Long {
            return getLongPreference(context!!, "last_snode_pool_refresh_date", 0)
        }

        fun setLastSnodePoolRefreshDate(context: Context?, date: Date) {
            setLongPreference(context!!, "last_snode_pool_refresh_date", date.time)
        }

        @JvmStatic
        fun shouldUpdateProfile(context: Context, profileUpdateTime: Long): Boolean {
            return profileUpdateTime > getLongPreference(context, LAST_PROFILE_UPDATE_TIME, 0)
        }

        @JvmStatic
        fun setLastProfileUpdateTime(context: Context, profileUpdateTime: Long) {
            setLongPreference(context, LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
        }

        fun getLastOpenTimeDate(context: Context): Long {
            return getLongPreference(context, LAST_OPEN_DATE, 0)
        }

        fun setLastOpenDate(context: Context) {
            setLongPreference(context, LAST_OPEN_DATE, System.currentTimeMillis())
        }

        @JvmStatic
        fun hasHiddenMessageRequests(context: Context): Boolean {
            return getBooleanPreference(context, HAS_HIDDEN_MESSAGE_REQUESTS, false)
        }

        @JvmStatic
        fun removeHasHiddenMessageRequests(context: Context) {
            removePreference(context, HAS_HIDDEN_MESSAGE_REQUESTS)
        }

        @JvmStatic
        fun isCallNotificationsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, CALL_NOTIFICATIONS_ENABLED, false)
        }

        @JvmStatic
        fun getLastVacuumTime(context: Context): Long {
            return getLongPreference(context, LAST_VACUUM_TIME, 0)
        }

        @JvmStatic
        fun setLastVacuumNow(context: Context) {
            setLongPreference(context, LAST_VACUUM_TIME, System.currentTimeMillis())
        }

        @JvmStatic
        fun getFingerprintKeyGenerated(context: Context): Boolean {
            return getBooleanPreference(context, FINGERPRINT_KEY_GENERATED, false)
        }

        @JvmStatic
        fun setFingerprintKeyGenerated(context: Context) {
            setBooleanPreference(context, FINGERPRINT_KEY_GENERATED, true)
        }

        @JvmStatic
        fun clearAll(context: Context) {
            getDefaultSharedPreferences(context).edit().clear().commit()
        }
    }

    fun getLastConfigurationSyncTime(): Long {
        return getLongPreference(TextSecurePreferences.LAST_CONFIGURATION_SYNC_TIME, 0)
    }

    fun setLastConfigurationSyncTime(value: Long) {
        setLongPreference(TextSecurePreferences.LAST_CONFIGURATION_SYNC_TIME, value)
    }

    fun getConfigurationMessageSynced(): Boolean {
        return getBooleanPreference(TextSecurePreferences.CONFIGURATION_SYNCED, false)
    }

    fun setConfigurationMessageSynced(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.CONFIGURATION_SYNCED, value)
        TextSecurePreferences._events.tryEmit(TextSecurePreferences.CONFIGURATION_SYNCED)
    }

    fun isPushEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.IS_PUSH_ENABLED, false)
    }

    fun setPushEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.IS_PUSH_ENABLED, value)
    }

    fun getPushToken(): String? {
        return getStringPreference(TextSecurePreferences.PUSH_TOKEN, "")
    }

    fun setPushToken(value: String) {
        setStringPreference(TextSecurePreferences.PUSH_TOKEN, value)
    }

    fun getPushRegisterTime(): Long {
        return getLongPreference(TextSecurePreferences.PUSH_REGISTER_TIME, 0)
    }

    fun setPushRegisterTime(value: Long) {
        setLongPreference(TextSecurePreferences.PUSH_REGISTER_TIME, value)
    }

    fun isScreenLockEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SCREEN_LOCK, false)
    }

    fun setScreenLockEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.SCREEN_LOCK, value)
    }

    fun getScreenLockTimeout(): Long {
        return getLongPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT, 0)
    }

    fun setScreenLockTimeout(value: Long) {
        setLongPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT, value)
    }

    fun setBackupPassphrase(passphrase: String?) {
        setStringPreference(TextSecurePreferences.BACKUP_PASSPHRASE, passphrase)
    }

    fun getBackupPassphrase(): String? {
        return getStringPreference(TextSecurePreferences.BACKUP_PASSPHRASE, null)
    }

    fun setEncryptedBackupPassphrase(encryptedPassphrase: String?) {
        setStringPreference(TextSecurePreferences.ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
    }

    fun getEncryptedBackupPassphrase(): String? {
        return getStringPreference(TextSecurePreferences.ENCRYPTED_BACKUP_PASSPHRASE, null)
    }

    fun setBackupEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.BACKUP_ENABLED, value)
    }

    fun isBackupEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.BACKUP_ENABLED, false)
    }

    fun setNextBackupTime(time: Long) {
        setLongPreference(TextSecurePreferences.BACKUP_TIME, time)
    }

    fun getNextBackupTime(): Long {
        return getLongPreference(TextSecurePreferences.BACKUP_TIME, -1)
    }

    fun setBackupSaveDir(dirUri: String?) {
        setStringPreference(TextSecurePreferences.BACKUP_SAVE_DIR, dirUri)
    }

    fun getBackupSaveDir(): String? {
        return getStringPreference(TextSecurePreferences.BACKUP_SAVE_DIR, null)
    }

    fun getNeedsSqlCipherMigration(): Boolean {
        return getBooleanPreference(TextSecurePreferences.NEEDS_SQLCIPHER_MIGRATION, false)
    }

    fun setAttachmentEncryptedSecret(secret: String) {
        setStringPreference(TextSecurePreferences.ATTACHMENT_ENCRYPTED_SECRET, secret)
    }

    fun setAttachmentUnencryptedSecret(secret: String?) {
        setStringPreference(TextSecurePreferences.ATTACHMENT_UNENCRYPTED_SECRET, secret)
    }

    fun getAttachmentEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.ATTACHMENT_ENCRYPTED_SECRET, null)
    }

    fun getAttachmentUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.ATTACHMENT_UNENCRYPTED_SECRET, null)
    }

    fun setDatabaseEncryptedSecret(secret: String) {
        setStringPreference(TextSecurePreferences.DATABASE_ENCRYPTED_SECRET, secret)
    }

    fun setDatabaseUnencryptedSecret(secret: String?) {
        setStringPreference(TextSecurePreferences.DATABASE_UNENCRYPTED_SECRET, secret)
    }

    fun getDatabaseUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.DATABASE_UNENCRYPTED_SECRET, null)
    }

    fun getDatabaseEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.DATABASE_ENCRYPTED_SECRET, null)
    }

    fun isIncognitoKeyboardEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.INCOGNITO_KEYBORAD_PREF, true)
    }

    fun isReadReceiptsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.READ_RECEIPTS_PREF, false)
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.READ_RECEIPTS_PREF, enabled)
    }

    fun isTypingIndicatorsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.TYPING_INDICATORS, false)
    }

    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.TYPING_INDICATORS, enabled)
    }

    fun isLinkPreviewsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.LINK_PREVIEWS, false)
    }

    fun setLinkPreviewsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.LINK_PREVIEWS, enabled)
    }

    fun hasSeenGIFMetaDataWarning(): Boolean {
        return getBooleanPreference(TextSecurePreferences.GIF_METADATA_WARNING, false)
    }

    fun setHasSeenGIFMetaDataWarning() {
        setBooleanPreference(TextSecurePreferences.GIF_METADATA_WARNING, true)
    }

    fun isGifSearchInGridLayout(): Boolean {
        return getBooleanPreference(TextSecurePreferences.GIF_GRID_LAYOUT, false)
    }

    fun setIsGifSearchInGridLayout(isGrid: Boolean) {
        setBooleanPreference(TextSecurePreferences.GIF_GRID_LAYOUT, isGrid)
    }

    fun getProfileKey(): String? {
        return getStringPreference(TextSecurePreferences.PROFILE_KEY_PREF, null)
    }

    fun setProfileKey(key: String?) {
        setStringPreference(TextSecurePreferences.PROFILE_KEY_PREF, key)
    }

    fun setProfileName(name: String?) {
        setStringPreference(TextSecurePreferences.PROFILE_NAME_PREF, name)
        TextSecurePreferences._events.tryEmit(TextSecurePreferences.PROFILE_NAME_PREF)
    }

    fun getProfileName(): String? {
        return getStringPreference(TextSecurePreferences.PROFILE_NAME_PREF, null)
    }

    fun setProfileAvatarId(id: Int) {
        setIntegerPreference(TextSecurePreferences.PROFILE_AVATAR_ID_PREF, id)
    }

    fun getProfileAvatarId(): Int {
        return getIntegerPreference(TextSecurePreferences.PROFILE_AVATAR_ID_PREF, 0)
    }

    fun setProfilePictureURL(url: String?) {
        setStringPreference(TextSecurePreferences.PROFILE_AVATAR_URL_PREF, url)
    }

    fun getProfilePictureURL(): String? {
        return getStringPreference(TextSecurePreferences.PROFILE_AVATAR_URL_PREF, null)
    }

    fun getNotificationPriority(): Int {
        return getStringPreference(
            TextSecurePreferences.NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
    }

    fun getMessageBodyTextSize(): Int {
        return getStringPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
    }

    fun setDirectCaptureCameraId(value: Int) {
        setIntegerPreference(TextSecurePreferences.DIRECT_CAPTURE_CAMERA_ID, value)
    }

    fun getDirectCaptureCameraId(): Int {
        return getIntegerPreference(TextSecurePreferences.DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK)
    }

    fun getNotificationPrivacy(): NotificationPrivacyPreference {
        return NotificationPrivacyPreference(getStringPreference(
            TextSecurePreferences.NOTIFICATION_PRIVACY_PREF, "all"))
    }

    fun getRepeatAlertsCount(): Int {
        return try {
            getStringPreference(TextSecurePreferences.REPEAT_ALERTS_PREF, "0")!!.toInt()
        } catch (e: NumberFormatException) {
            Log.w(TextSecurePreferences.TAG, e)
            0
        }
    }

    fun getLocalRegistrationId(): Int {
        return getIntegerPreference(TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF, 0)
    }

    fun setLocalRegistrationId(registrationId: Int) {
        setIntegerPreference(TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF, registrationId)
    }

    fun isInThreadNotifications(): Boolean {
        return getBooleanPreference(TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF, true)
    }

    fun isUniversalUnidentifiedAccess(): Boolean {
        return getBooleanPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, false)
    }

    fun getUpdateApkRefreshTime(): Long {
        return getLongPreference(TextSecurePreferences.UPDATE_APK_REFRESH_TIME_PREF, 0L)
    }

    fun setUpdateApkRefreshTime(value: Long) {
        setLongPreference(TextSecurePreferences.UPDATE_APK_REFRESH_TIME_PREF, value)
    }

    fun setUpdateApkDownloadId(value: Long) {
        setLongPreference(TextSecurePreferences.UPDATE_APK_DOWNLOAD_ID, value)
    }

    fun getUpdateApkDownloadId(): Long {
        return getLongPreference(TextSecurePreferences.UPDATE_APK_DOWNLOAD_ID, -1)
    }

    fun setUpdateApkDigest(value: String?) {
        setStringPreference(TextSecurePreferences.UPDATE_APK_DIGEST, value)
    }

    fun getUpdateApkDigest(): String? {
        return getStringPreference(TextSecurePreferences.UPDATE_APK_DIGEST, null)
    }

    fun getLocalNumber(): String? {
        return getStringPreference(TextSecurePreferences.LOCAL_NUMBER_PREF, null)
    }

    fun getHasLegacyConfig(): Boolean {
        return getBooleanPreference(TextSecurePreferences.HAS_RECEIVED_LEGACY_CONFIG, false)
    }

    fun setHasLegacyConfig(newValue: Boolean) {
        setBooleanPreference(TextSecurePreferences.HAS_RECEIVED_LEGACY_CONFIG, newValue)
        TextSecurePreferences._events.tryEmit(TextSecurePreferences.HAS_RECEIVED_LEGACY_CONFIG)
    }

    fun setLocalNumber(localNumber: String) {
        setStringPreference(TextSecurePreferences.LOCAL_NUMBER_PREF, localNumber.toLowerCase())
    }

    fun removeLocalNumber() {
        removePreference(TextSecurePreferences.LOCAL_NUMBER_PREF)
    }

    fun isEnterSendsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.ENTER_SENDS_PREF, false)
    }

    fun isPasswordDisabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF, true)
    }

    fun setPasswordDisabled(disabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF, disabled)
    }

    fun isScreenSecurityEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SCREEN_SECURITY_PREF, true)
    }

    fun getLastVersionCode(): Int {
        return getIntegerPreference(TextSecurePreferences.LAST_VERSION_CODE_PREF, 0)
    }

    @Throws(IOException::class)
    fun setLastVersionCode(versionCode: Int) {
        if (!setIntegerPreferenceBlocking(TextSecurePreferences.LAST_VERSION_CODE_PREF, versionCode)) {
            throw IOException("couldn't write version code to sharedpreferences")
        }
    }

    fun isPassphraseTimeoutEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF, false)
    }

    fun getPassphraseTimeoutInterval(): Int {
        return getIntegerPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
    }

    fun getLanguage(): String? {
        return getStringPreference(TextSecurePreferences.LANGUAGE_PREF, "zz")
    }

    fun isNotificationsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.NOTIFICATION_PREF, true)
    }

    fun getNotificationRingtone(): Uri {
        var result = getStringPreference(TextSecurePreferences.RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
        if (result != null && result.startsWith("file:")) {
            result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
        }
        return Uri.parse(result)
    }

    fun removeNotificationRingtone() {
        removePreference(TextSecurePreferences.RINGTONE_PREF)
    }

    fun setNotificationRingtone(ringtone: String?) {
        setStringPreference(TextSecurePreferences.RINGTONE_PREF, ringtone)
    }

    fun setNotificationVibrateEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.VIBRATE_PREF, enabled)
    }

    fun isNotificationVibrateEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.VIBRATE_PREF, true)
    }

    fun getNotificationLedColor(): Int {
        return getIntegerPreference(TextSecurePreferences.LED_COLOR_PREF_PRIMARY, context.getColor(R.color.accent_green))
    }

    fun isThreadLengthTrimmingEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.THREAD_TRIM_ENABLED, true)
    }

    fun isSystemEmojiPreferred(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SYSTEM_EMOJI_PREF, false)
    }

    fun getMobileMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default)
    }

    fun getWifiMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default)
    }

    fun getRoamingMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default)
    }

    fun getMediaDownloadAllowed(key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
        return getStringSetPreference(key, HashSet(listOf(*context.resources.getStringArray(defaultValuesRes))))
    }

    fun getLogEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.LOG_ENCRYPTED_SECRET, null)
    }

    fun setLogEncryptedSecret(base64Secret: String?) {
        setStringPreference(TextSecurePreferences.LOG_ENCRYPTED_SECRET, base64Secret)
    }

    fun getLogUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.LOG_UNENCRYPTED_SECRET, null)
    }

    fun setLogUnencryptedSecret(base64Secret: String?) {
        setStringPreference(TextSecurePreferences.LOG_UNENCRYPTED_SECRET, base64Secret)
    }

    fun getNotificationChannelVersion(): Int {
        return getIntegerPreference(TextSecurePreferences.NOTIFICATION_CHANNEL_VERSION, 1)
    }

    fun setNotificationChannelVersion(version: Int) {
        setIntegerPreference(TextSecurePreferences.NOTIFICATION_CHANNEL_VERSION, version)
    }

    fun getNotificationMessagesChannelVersion(): Int {
        return getIntegerPreference(TextSecurePreferences.NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
    }

    fun setNotificationMessagesChannelVersion(version: Int) {
        setIntegerPreference(TextSecurePreferences.NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    }

    fun hasForcedNewConfig(): Boolean =
        getBooleanPreference(TextSecurePreferences.HAS_FORCED_NEW_CONFIG, false)

    fun getBooleanPreference(key: String?, defaultValue: Boolean): Boolean {
        return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
    }

    fun setBooleanPreference(key: String?, value: Boolean) {
        getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    fun getStringPreference(key: String, defaultValue: String?): String? {
        return getDefaultSharedPreferences(context).getString(key, defaultValue)
    }

    fun setStringPreference(key: String?, value: String?) {
        getDefaultSharedPreferences(context).edit().putString(key, value).apply()
    }

    fun getIntegerPreference(key: String, defaultValue: Int): Int {
        return getDefaultSharedPreferences(context).getInt(key, defaultValue)
    }

    fun setIntegerPreference(key: String, value: Int) {
        getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
    }

    fun setIntegerPreferenceBlocking(key: String, value: Int): Boolean {
        return getDefaultSharedPreferences(context).edit().putInt(key, value).commit()
    }

    fun getLongPreference(key: String, defaultValue: Long): Long {
        return getDefaultSharedPreferences(context).getLong(key, defaultValue)
    }

    fun setLongPreference(key: String, value: Long) {
        getDefaultSharedPreferences(context).edit().putLong(key, value).apply()
    }

    fun hasPreference(key: String): Boolean {
        return getDefaultSharedPreferences(context).contains(key)
    }

    fun removePreference(key: String) {
        getDefaultSharedPreferences(context).edit().remove(key).apply()
    }

    fun getStringSetPreference(key: String, defaultValues: Set<String>): Set<String>? {
        val prefs = getDefaultSharedPreferences(context)
        return if (prefs.contains(key)) {
            prefs.getStringSet(key, emptySet())
        } else {
            defaultValues
        }
    }

    fun getHasViewedSeed(): Boolean {
        return getBooleanPreference("has_viewed_seed", false)
    }

    fun setHasViewedSeed(hasViewedSeed: Boolean) {
        setBooleanPreference("has_viewed_seed", hasViewedSeed)
    }

    fun setRestorationTime(time: Long) {
        setLongPreference("restoration_time", time)
    }

    fun getRestorationTime(): Long {
        return getLongPreference("restoration_time", 0)
    }

    fun getLastProfilePictureUpload(): Long {
        return getLongPreference("last_profile_picture_upload", 0)
    }

    fun setLastProfilePictureUpload(newValue: Long) {
        setLongPreference("last_profile_picture_upload", newValue)
    }

    fun getLastSnodePoolRefreshDate(): Long {
        return getLongPreference("last_snode_pool_refresh_date", 0)
    }

    fun setLastSnodePoolRefreshDate(date: Date) {
        setLongPreference("last_snode_pool_refresh_date", date.time)
    }

    fun shouldUpdateProfile(profileUpdateTime: Long): Boolean {
        return profileUpdateTime > getLongPreference(TextSecurePreferences.LAST_PROFILE_UPDATE_TIME, 0)
    }

    fun setLastProfileUpdateTime(profileUpdateTime: Long) {
        setLongPreference(TextSecurePreferences.LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
    }

    fun getLastOpenTimeDate(): Long {
        return getLongPreference(TextSecurePreferences.LAST_OPEN_DATE, 0)
    }

    fun setLastOpenDate() {
        setLongPreference(TextSecurePreferences.LAST_OPEN_DATE, System.currentTimeMillis())
    }

    fun hasSeenLinkPreviewSuggestionDialog(): Boolean {
        return getBooleanPreference("has_seen_link_preview_suggestion_dialog", false)
    }

    fun setHasSeenLinkPreviewSuggestionDialog() {
        setBooleanPreference("has_seen_link_preview_suggestion_dialog", true)
    }

    fun isCallNotificationsEnabled(): Boolean {
        return getBooleanPreference(CALL_NOTIFICATIONS_ENABLED, false)
    }

    fun getLastVacuum(): Long {
        return getLongPreference(LAST_VACUUM_TIME, 0)
    }

    fun setLastVacuumNow() {
        setLongPreference(LAST_VACUUM_TIME, System.currentTimeMillis())
    }

    fun setShownCallNotification(): Boolean {
        val previousValue = getBooleanPreference(SHOWN_CALL_NOTIFICATION, false)
        if (previousValue) return false
        val setValue = true
        setBooleanPreference(SHOWN_CALL_NOTIFICATION, setValue)
        return previousValue != setValue
    }


    /**
     * Set the SHOWN_CALL_WARNING preference to `true`
     * Return `true` if the value did update (it was previously unset)
     */
    fun setShownCallWarning() : Boolean {
        val previousValue = getBooleanPreference(SHOWN_CALL_WARNING, false)
        if (previousValue) {
            return false
        }
        val setValue = true
        setBooleanPreference(SHOWN_CALL_WARNING, setValue)
        return previousValue != setValue
    }

    fun hasHiddenMessageRequests(): Boolean {
        return getBooleanPreference(TextSecurePreferences.HAS_HIDDEN_MESSAGE_REQUESTS, false)
    }

    fun setHasHiddenMessageRequests() {
        setBooleanPreference(TextSecurePreferences.HAS_HIDDEN_MESSAGE_REQUESTS, true)
    }

    fun getFingerprintKeyGenerated(): Boolean {
        return getBooleanPreference(TextSecurePreferences.FINGERPRINT_KEY_GENERATED, false)
    }

    fun setFingerprintKeyGenerated() {
        setBooleanPreference(TextSecurePreferences.FINGERPRINT_KEY_GENERATED, true)
    }

    fun getSelectedAccentColor(): String? =
        getStringPreference(SELECTED_ACCENT_COLOR, null)

    @StyleRes
    fun getAccentColorStyle(): Int? {
        return when (getSelectedAccentColor()) {
            TextSecurePreferences.GREEN_ACCENT -> R.style.PrimaryGreen
            TextSecurePreferences.BLUE_ACCENT -> R.style.PrimaryBlue
            TextSecurePreferences.PURPLE_ACCENT -> R.style.PrimaryPurple
            TextSecurePreferences.PINK_ACCENT -> R.style.PrimaryPink
            TextSecurePreferences.RED_ACCENT -> R.style.PrimaryRed
            TextSecurePreferences.ORANGE_ACCENT -> R.style.PrimaryOrange
            TextSecurePreferences.YELLOW_ACCENT -> R.style.PrimaryYellow
            else -> null
        }
    }

    fun setAccentColorStyle(@StyleRes newColorStyle: Int?) {
        setStringPreference(
            TextSecurePreferences.SELECTED_ACCENT_COLOR, when (newColorStyle) {
                R.style.PrimaryGreen -> TextSecurePreferences.GREEN_ACCENT
                R.style.PrimaryBlue -> TextSecurePreferences.BLUE_ACCENT
                R.style.PrimaryPurple -> TextSecurePreferences.PURPLE_ACCENT
                R.style.PrimaryPink -> TextSecurePreferences.PINK_ACCENT
                R.style.PrimaryRed -> TextSecurePreferences.RED_ACCENT
                R.style.PrimaryOrange -> TextSecurePreferences.ORANGE_ACCENT
                R.style.PrimaryYellow -> TextSecurePreferences.YELLOW_ACCENT
                else -> null
            }
        )
    }

    fun getThemeStyle(): String {
        val hasLegacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null)
        if (!hasLegacy.isNullOrEmpty()) {
            migrateLegacyUiPref()
        }

        return getStringPreference(SELECTED_STYLE, CLASSIC_DARK)!!
    }

    fun setThemeStyle(themeStyle: String) {
        val safeTheme = if (themeStyle !in listOf(CLASSIC_DARK, CLASSIC_LIGHT, OCEAN_DARK, OCEAN_LIGHT)) CLASSIC_DARK else themeStyle
        setStringPreference(SELECTED_STYLE, safeTheme)
    }

    fun getFollowSystemSettings(): Boolean {
        val hasLegacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null)
        if (!hasLegacy.isNullOrEmpty()) {
            migrateLegacyUiPref()
        }

        return getBooleanPreference(FOLLOW_SYSTEM_SETTINGS, false)
    }

    private fun migrateLegacyUiPref() {
        val legacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null) ?: return
        val (mode, followSystem) = when (legacy) {
            "DAY" -> {
                CLASSIC_LIGHT to false
            }
            "NIGHT" -> {
                CLASSIC_DARK to false
            }
            "SYSTEM_DEFAULT" -> {
                CLASSIC_DARK to true
            }
            else -> {
                CLASSIC_DARK to false
            }
        }
        if (!hasPreference(FOLLOW_SYSTEM_SETTINGS) && !hasPreference(SELECTED_STYLE)) {
            setThemeStyle(mode)
            setFollowSystemSettings(followSystem)
        }
        removePreference(LEGACY_PREF_KEY_SELECTED_UI_MODE)
    }

    fun setFollowSystemSettings(followSystemSettings: Boolean) {
        setBooleanPreference(FOLLOW_SYSTEM_SETTINGS, followSystemSettings)
    }

    fun autoplayAudioMessages(): Boolean {
        return getBooleanPreference(AUTOPLAY_AUDIO_MESSAGES, false)
    }

    fun clearAll() {
        getDefaultSharedPreferences(context).edit().clear().commit()
    }

    fun getHidePassword() = getBooleanPreference(HIDE_PASSWORD, false)

    fun setHidePassword(value: Boolean) {
        setBooleanPreference(HIDE_PASSWORD, value)
    }
}
