package org.session.libsession.utilities

import android.content.Context
import android.content.SharedPreferences
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
import org.session.libsession.utilities.TextSecurePreferences.Companion.instance
import org.session.libsignal.utilities.Log
import java.io.IOException
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
    fun set(value: T, prefs: SharedPreferences) = prefs.edit().set(name, value).apply()
}

fun Pref(name: String, default: Boolean) = Pref(name, default, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)
fun Pref(name: String, default: Int) = Pref(name, default, SharedPreferences::getInt, SharedPreferences.Editor::putInt)
fun Pref(name: String, default: Long) = Pref(name, default, SharedPreferences::getLong, SharedPreferences.Editor::putLong)
fun Pref(name: String, default: String) = Pref(name, default, SharedPreferences::getString, SharedPreferences.Editor::putStringOrRemove)
fun Pref(name: String) = Pref(name, null, SharedPreferences::getString, SharedPreferences.Editor::putStringOrRemove)

private fun SharedPreferences.Editor.putStringOrRemove(name: String, value: String?) = value?.let { putString(name, it) } ?: run { remove(name) }

operator fun <T> SharedPreferences.get(pref: Pref<T>): T = pref.get(this)
operator fun <T> SharedPreferences.set(pref: Pref<T>, value: T) = pref.set(value, this)

val Context.prefs get() = instance ?: TextSecurePreferences(this).also { instance = it }

@Singleton
class TextSecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val Context.prefs get() = TextSecurePreferences(this)

    private val sharedPreferences = getDefaultSharedPreferences(context)

    operator fun <T> get(pref: Pref<T>): T = sharedPreferences[pref]

    companion object {
        val TAG = TextSecurePreferences::class.simpleName

        var instance: TextSecurePreferences? = null

        internal val _events = MutableSharedFlow<String>(0, 64, BufferOverflow.DROP_OLDEST)
        val events get() = _events.asSharedFlow()

        @JvmStatic
        var pushSuffix = ""

        val DISABLE_PASSPHRASE_PREF = Pref("pref_disable_passphrase", true)
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
    }

    fun getLastConfigurationSyncTime(): Long {
        return getLongPreference(LAST_CONFIGURATION_SYNC_TIME, 0)
    }

    fun setLastConfigurationSyncTime(value: Long) {
        setLongPreference(LAST_CONFIGURATION_SYNC_TIME, value)
    }

    fun getConfigurationMessageSynced(): Boolean {
        return getBooleanPreference(CONFIGURATION_SYNCED, false)
    }

    fun setConfigurationMessageSynced(value: Boolean) {
        setBooleanPreference(CONFIGURATION_SYNCED, value)
        _events.tryEmit(CONFIGURATION_SYNCED)
    }

    fun isPushEnabled(): Boolean {
        return getBooleanPreference(IS_PUSH_ENABLED, false)
    }

    fun setPushEnabled(value: Boolean) {
        setBooleanPreference(IS_PUSH_ENABLED, value)
    }

    fun getPushToken(): String? {
        return getStringPreference(PUSH_TOKEN, "")
    }

    fun setPushToken(value: String) {
        setStringPreference(PUSH_TOKEN, value)
    }

    fun getPushRegisterTime(): Long {
        return getLongPreference(PUSH_REGISTER_TIME, 0)
    }

    fun setPushRegisterTime(value: Long) {
        setLongPreference(PUSH_REGISTER_TIME, value)
    }

    fun isScreenLockEnabled(): Boolean {
        return getBooleanPreference(SCREEN_LOCK, false)
    }

    fun setScreenLockEnabled(value: Boolean) {
        setBooleanPreference(SCREEN_LOCK, value)
    }

    fun getScreenLockTimeout(): Long {
        return getLongPreference(SCREEN_LOCK_TIMEOUT, 0)
    }

    fun setScreenLockTimeout(value: Long) {
        setLongPreference(SCREEN_LOCK_TIMEOUT, value)
    }

    fun setBackupPassphrase(passphrase: String?) {
        setStringPreference(BACKUP_PASSPHRASE, passphrase)
    }

    fun getBackupPassphrase(): String? {
        return getStringPreference(BACKUP_PASSPHRASE, null)
    }

    fun setEncryptedBackupPassphrase(encryptedPassphrase: String?) {
        setStringPreference(ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
    }

    fun getEncryptedBackupPassphrase(): String? {
        return getStringPreference(ENCRYPTED_BACKUP_PASSPHRASE, null)
    }

    fun setBackupEnabled(value: Boolean) {
        setBooleanPreference(BACKUP_ENABLED, value)
    }

    fun isBackupEnabled(): Boolean {
        return getBooleanPreference(BACKUP_ENABLED, false)
    }

    fun setNextBackupTime(time: Long) {
        setLongPreference(BACKUP_TIME, time)
    }

    fun getNextBackupTime(): Long {
        return getLongPreference(BACKUP_TIME, -1)
    }

    fun setBackupSaveDir(dirUri: String?) {
        setStringPreference(BACKUP_SAVE_DIR, dirUri)
    }

    fun getBackupSaveDir(): String? {
        return getStringPreference(BACKUP_SAVE_DIR, null)
    }

    fun getNeedsSqlCipherMigration(): Boolean {
        return getBooleanPreference(NEEDS_SQLCIPHER_MIGRATION, false)
    }

    fun setAttachmentEncryptedSecret(secret: String) {
        setStringPreference(ATTACHMENT_ENCRYPTED_SECRET, secret)
    }

    fun setAttachmentUnencryptedSecret(secret: String?) {
        setStringPreference(ATTACHMENT_UNENCRYPTED_SECRET, secret)
    }

    fun getAttachmentEncryptedSecret(): String? {
        return getStringPreference(ATTACHMENT_ENCRYPTED_SECRET, null)
    }

    fun getAttachmentUnencryptedSecret(): String? {
        return getStringPreference(ATTACHMENT_UNENCRYPTED_SECRET, null)
    }

    fun setDatabaseEncryptedSecret(secret: String) {
        setStringPreference(DATABASE_ENCRYPTED_SECRET, secret)
    }

    fun setDatabaseUnencryptedSecret(secret: String?) {
        setStringPreference(DATABASE_UNENCRYPTED_SECRET, secret)
    }

    fun getDatabaseUnencryptedSecret(): String? {
        return getStringPreference(DATABASE_UNENCRYPTED_SECRET, null)
    }

    fun getDatabaseEncryptedSecret(): String? {
        return getStringPreference(DATABASE_ENCRYPTED_SECRET, null)
    }

    fun isIncognitoKeyboardEnabled(): Boolean {
        return getBooleanPreference(INCOGNITO_KEYBORAD_PREF, true)
    }

    fun isReadReceiptsEnabled(): Boolean {
        return getBooleanPreference(READ_RECEIPTS_PREF, false)
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        setBooleanPreference(READ_RECEIPTS_PREF, enabled)
    }

    fun isTypingIndicatorsEnabled(): Boolean {
        return getBooleanPreference(TYPING_INDICATORS, false)
    }

    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        setBooleanPreference(TYPING_INDICATORS, enabled)
    }

    fun isLinkPreviewsEnabled(): Boolean {
        return getBooleanPreference(LINK_PREVIEWS, false)
    }

    fun setLinkPreviewsEnabled(enabled: Boolean) {
        setBooleanPreference(LINK_PREVIEWS, enabled)
    }

    fun hasSeenGIFMetaDataWarning(): Boolean {
        return getBooleanPreference(GIF_METADATA_WARNING, false)
    }

    fun setHasSeenGIFMetaDataWarning() {
        setBooleanPreference(GIF_METADATA_WARNING, true)
    }

    fun isGifSearchInGridLayout(): Boolean {
        return getBooleanPreference(GIF_GRID_LAYOUT, false)
    }

    fun setIsGifSearchInGridLayout(isGrid: Boolean) {
        setBooleanPreference(GIF_GRID_LAYOUT, isGrid)
    }

    fun getProfileKey(): String? {
        return getStringPreference(PROFILE_KEY_PREF, null)
    }

    fun setProfileKey(key: String?) {
        setStringPreference(PROFILE_KEY_PREF, key)
    }

    fun setProfileName(name: String?) {
        setStringPreference(PROFILE_NAME_PREF, name)
        _events.tryEmit(PROFILE_NAME_PREF)
    }

    fun getProfileName(): String? {
        return getStringPreference(PROFILE_NAME_PREF, null)
    }

    fun setProfileAvatarId(id: Int) {
        setIntegerPreference(PROFILE_AVATAR_ID_PREF, id)
    }

    fun getProfileAvatarId(): Int {
        return getIntegerPreference(PROFILE_AVATAR_ID_PREF, 0)
    }

    fun setProfilePictureURL(url: String?) {
        setStringPreference(PROFILE_AVATAR_URL_PREF, url)
    }

    fun getProfilePictureURL(): String? {
        return getStringPreference(PROFILE_AVATAR_URL_PREF, null)
    }

    fun getNotificationPriority(): Int {
        return getStringPreference(
            NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
    }

    fun getMessageBodyTextSize(): Int {
        return getStringPreference(MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
    }

    fun setDirectCaptureCameraId(value: Int) {
        setIntegerPreference(DIRECT_CAPTURE_CAMERA_ID, value)
    }

    fun getDirectCaptureCameraId(): Int {
        return getIntegerPreference(DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK)
    }

    fun getNotificationPrivacy(): NotificationPrivacyPreference {
        return NotificationPrivacyPreference(getStringPreference(
            NOTIFICATION_PRIVACY_PREF, "all"))
    }

    fun getRepeatAlertsCount(): Int {
        return try {
            getStringPreference(REPEAT_ALERTS_PREF, "0")!!.toInt()
        } catch (e: NumberFormatException) {
            Log.w(TAG, e)
            0
        }
    }

    fun getLocalRegistrationId(): Int {
        return getIntegerPreference(LOCAL_REGISTRATION_ID_PREF, 0)
    }

    fun setLocalRegistrationId(registrationId: Int) {
        setIntegerPreference(LOCAL_REGISTRATION_ID_PREF, registrationId)
    }

    fun isInThreadNotifications(): Boolean {
        return getBooleanPreference(IN_THREAD_NOTIFICATION_PREF, true)
    }

    fun isUniversalUnidentifiedAccess(): Boolean {
        return getBooleanPreference(UNIVERSAL_UNIDENTIFIED_ACCESS, false)
    }

    fun getUpdateApkRefreshTime(): Long {
        return getLongPreference(UPDATE_APK_REFRESH_TIME_PREF, 0L)
    }

    fun setUpdateApkRefreshTime(value: Long) {
        setLongPreference(UPDATE_APK_REFRESH_TIME_PREF, value)
    }

    fun setUpdateApkDownloadId(value: Long) {
        setLongPreference(UPDATE_APK_DOWNLOAD_ID, value)
    }

    fun getUpdateApkDownloadId(): Long {
        return getLongPreference(UPDATE_APK_DOWNLOAD_ID, -1)
    }

    fun setUpdateApkDigest(value: String?) {
        setStringPreference(UPDATE_APK_DIGEST, value)
    }

    fun getUpdateApkDigest(): String? {
        return getStringPreference(UPDATE_APK_DIGEST, null)
    }

    fun getLocalNumber(): String? {
        return getStringPreference(LOCAL_NUMBER_PREF, null)
    }

    fun getHasLegacyConfig(): Boolean {
        return getBooleanPreference(HAS_RECEIVED_LEGACY_CONFIG, false)
    }

    fun setHasLegacyConfig(newValue: Boolean) {
        setBooleanPreference(HAS_RECEIVED_LEGACY_CONFIG, newValue)
        _events.tryEmit(HAS_RECEIVED_LEGACY_CONFIG)
    }

    fun setLocalNumber(localNumber: String) {
        setStringPreference(LOCAL_NUMBER_PREF, localNumber.toLowerCase())
    }

    fun removeLocalNumber() {
        removePreference(LOCAL_NUMBER_PREF)
    }

    fun isEnterSendsEnabled(): Boolean {
        return getBooleanPreference(ENTER_SENDS_PREF, false)
    }

    fun isPasswordDisabled(): Boolean = sharedPreferences[DISABLE_PASSPHRASE_PREF]

    fun setPasswordDisabled(disabled: Boolean) {
        sharedPreferences[DISABLE_PASSPHRASE_PREF] = disabled
    }

    fun isScreenSecurityEnabled(): Boolean {
        return getBooleanPreference(SCREEN_SECURITY_PREF, true)
    }

    fun getLastVersionCode(): Int {
        return getIntegerPreference(LAST_VERSION_CODE_PREF, 0)
    }

    @Throws(IOException::class)
    fun setLastVersionCode(versionCode: Int) {
        if (!setIntegerPreferenceBlocking(LAST_VERSION_CODE_PREF, versionCode)) {
            throw IOException("couldn't write version code to sharedpreferences")
        }
    }

    fun isPassphraseTimeoutEnabled(): Boolean {
        return getBooleanPreference(PASSPHRASE_TIMEOUT_PREF, false)
    }

    fun getPassphraseTimeoutInterval(): Int {
        return getIntegerPreference(PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
    }

    fun getLanguage(): String? {
        return getStringPreference(LANGUAGE_PREF, "zz")
    }

    fun isNotificationsEnabled(): Boolean {
        return getBooleanPreference(NOTIFICATION_PREF, true)
    }

    fun getNotificationRingtone(): Uri {
        var result = getStringPreference(RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
        if (result != null && result.startsWith("file:")) {
            result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
        }
        return Uri.parse(result)
    }

    fun removeNotificationRingtone() {
        removePreference(RINGTONE_PREF)
    }

    fun setNotificationRingtone(ringtone: String?) {
        setStringPreference(RINGTONE_PREF, ringtone)
    }

    fun setNotificationVibrateEnabled(enabled: Boolean) {
        setBooleanPreference(VIBRATE_PREF, enabled)
    }

    fun isNotificationVibrateEnabled(): Boolean {
        return getBooleanPreference(VIBRATE_PREF, true)
    }

    fun getNotificationLedColor(): Int {
        return getIntegerPreference(LED_COLOR_PREF_PRIMARY, context.getColor(R.color.accent_green))
    }

    fun isThreadLengthTrimmingEnabled(): Boolean {
        return getBooleanPreference(THREAD_TRIM_ENABLED, true)
    }

    fun isSystemEmojiPreferred(): Boolean {
        return getBooleanPreference(SYSTEM_EMOJI_PREF, false)
    }

    fun getMobileMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default)
    }

    fun getWifiMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default)
    }

    fun getRoamingMediaDownloadAllowed(): Set<String>? {
        return getMediaDownloadAllowed(MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default)
    }

    fun getMediaDownloadAllowed(key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
        return getStringSetPreference(key, HashSet(listOf(*context.resources.getStringArray(defaultValuesRes))))
    }

    fun getLogEncryptedSecret(): String? {
        return getStringPreference(LOG_ENCRYPTED_SECRET, null)
    }

    fun setLogEncryptedSecret(base64Secret: String?) {
        setStringPreference(LOG_ENCRYPTED_SECRET, base64Secret)
    }

    fun getLogUnencryptedSecret(): String? {
        return getStringPreference(LOG_UNENCRYPTED_SECRET, null)
    }

    fun setLogUnencryptedSecret(base64Secret: String?) {
        setStringPreference(LOG_UNENCRYPTED_SECRET, base64Secret)
    }

    fun getNotificationChannelVersion(): Int {
        return getIntegerPreference(NOTIFICATION_CHANNEL_VERSION, 1)
    }

    fun setNotificationChannelVersion(version: Int) {
        setIntegerPreference(NOTIFICATION_CHANNEL_VERSION, version)
    }

    fun getNotificationMessagesChannelVersion(): Int {
        return getIntegerPreference(NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
    }

    fun setNotificationMessagesChannelVersion(version: Int) {
        setIntegerPreference(NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    }

    fun hasForcedNewConfig(): Boolean =
        getBooleanPreference(HAS_FORCED_NEW_CONFIG, false)

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
        return profileUpdateTime > getLongPreference(LAST_PROFILE_UPDATE_TIME, 0)
    }

    fun setLastProfileUpdateTime(profileUpdateTime: Long) {
        setLongPreference(LAST_PROFILE_UPDATE_TIME, profileUpdateTime)
    }

    fun getLastOpenTimeDate(): Long {
        return getLongPreference(LAST_OPEN_DATE, 0)
    }

    fun setLastOpenDate() {
        setLongPreference(LAST_OPEN_DATE, System.currentTimeMillis())
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

    fun setCallNotificationsEnabled(enabled: Boolean) {
        setBooleanPreference(CALL_NOTIFICATIONS_ENABLED, enabled)
    }

    fun getLastVacuumTime(): Long {
        return getLongPreference(LAST_VACUUM_TIME, 0)
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
        return getBooleanPreference(HAS_HIDDEN_MESSAGE_REQUESTS, false)
    }

    fun setHasHiddenMessageRequests() {
        setBooleanPreference(HAS_HIDDEN_MESSAGE_REQUESTS, true)
    }

    fun removeHasHiddenMessageRequests() {
        removePreference(HAS_HIDDEN_MESSAGE_REQUESTS)
    }

    fun getFingerprintKeyGenerated(): Boolean {
        return getBooleanPreference(FINGERPRINT_KEY_GENERATED, false)
    }

    fun setFingerprintKeyGenerated() {
        setBooleanPreference(FINGERPRINT_KEY_GENERATED, true)
    }

    fun getSelectedAccentColor(): String? =
        getStringPreference(SELECTED_ACCENT_COLOR, null)

    @StyleRes
    fun getAccentColorStyle(): Int? {
        return when (getSelectedAccentColor()) {
            GREEN_ACCENT -> R.style.PrimaryGreen
            BLUE_ACCENT -> R.style.PrimaryBlue
            PURPLE_ACCENT -> R.style.PrimaryPurple
            PINK_ACCENT -> R.style.PrimaryPink
            RED_ACCENT -> R.style.PrimaryRed
            ORANGE_ACCENT -> R.style.PrimaryOrange
            YELLOW_ACCENT -> R.style.PrimaryYellow
            else -> null
        }
    }

    fun setAccentColorStyle(@StyleRes newColorStyle: Int?) {
        setStringPreference(
            SELECTED_ACCENT_COLOR, when (newColorStyle) {
                R.style.PrimaryGreen -> GREEN_ACCENT
                R.style.PrimaryBlue -> BLUE_ACCENT
                R.style.PrimaryPurple -> PURPLE_ACCENT
                R.style.PrimaryPink -> PINK_ACCENT
                R.style.PrimaryRed -> RED_ACCENT
                R.style.PrimaryOrange -> ORANGE_ACCENT
                R.style.PrimaryYellow -> YELLOW_ACCENT
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
