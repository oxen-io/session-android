package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivitySettingsBinding
import network.loki.messenger.libsession_util.util.UserPic
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.ProfilePictureUtilities
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.avatar.AvatarSelection
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.home.PathActivity
import org.thoughtcrime.securesms.messagerequests.MessageRequestsActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.appearance.AppearanceSettingsActivity
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints
import org.thoughtcrime.securesms.recoverypassword.RecoveryPasswordActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.LargeItemButton
import org.thoughtcrime.securesms.ui.LargeItemButtonWithDrawable
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.dangerButtonColors
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineCopyButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.setThemedContent
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.NetworkUtils
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.show
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : PassphraseRequiredActionBarActivity() {
    private val TAG = "SettingsActivity"

    @Inject
    lateinit var configFactory: ConfigFactory
    @Inject
    lateinit var prefs: TextSecurePreferences

    private lateinit var binding: ActivitySettingsBinding
    private var displayNameEditActionMode: ActionMode? = null
        set(value) { field = value; handleDisplayNameEditActionModeChanged() }
    private var tempFile: File? = null

    private val hexEncodedPublicKey: String get() = TextSecurePreferences.getLocalNumber(this)!!

    companion object {
        private const val SCROLL_STATE = "SCROLL_STATE"
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()

        binding.run {
            setupProfilePictureView(profilePictureView)
            profilePictureView.setOnClickListener { showEditProfilePictureUI() }
            ctnGroupNameSection.setOnClickListener { startActionMode(DisplayNameEditActionModeCallback()) }
            btnGroupNameDisplay.text = getDisplayName()
            publicKeyTextView.text = hexEncodedPublicKey
            val gitCommitFirstSixChars = BuildConfig.GIT_HASH.take(6)
            versionTextView.text = String.format(getString(R.string.version_s), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE} - $gitCommitFirstSixChars)")
        }

        binding.composeView.setThemedContent {
            Buttons()
        }
    }

    private fun getDisplayName(): String =
        TextSecurePreferences.getProfileName(this) ?: truncateIdForDisplay(hexEncodedPublicKey)

    private fun setupProfilePictureView(view: ProfilePictureView) {
        view.apply {
            publicKey = hexEncodedPublicKey
            displayName = getDisplayName()
            update()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val scrollBundle = SparseArray<Parcelable>()
        binding.scrollView.saveHierarchyState(scrollBundle)
        outState.putSparseParcelableArray(SCROLL_STATE, scrollBundle)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getSparseParcelableArray<Parcelable>(SCROLL_STATE)?.let { scrollBundle ->
            binding.scrollView.restoreHierarchyState(scrollBundle)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_general, menu)
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.action_qr_code)?.contentDescription = resources.getString(R.string.AccessibilityId_view_qr_code)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_qr_code -> {
                push<QRCodeActivity>()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            AvatarSelection.REQUEST_CODE_AVATAR -> {
                val outputFile = Uri.fromFile(File(cacheDir, "cropped"))
                val inputFile: Uri? = data?.data ?: tempFile?.let(Uri::fromFile)
                AvatarSelection.circularCropImage(this, inputFile, outputFile, R.string.CropImageActivity_profile_avatar)
            }
            AvatarSelection.REQUEST_CODE_CROP_IMAGE -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val profilePictureToBeUploaded = BitmapUtil.createScaledBytes(this@SettingsActivity, AvatarSelection.getResultUri(data), ProfileMediaConstraints()).bitmap
                        launch(Dispatchers.Main) {
                            updateProfilePicture(profilePictureToBeUploaded)
                        }
                    } catch (e: BitmapDecodingException) {
                        Log.e(TAG, e)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    // endregion

    // region Updating
    private fun handleDisplayNameEditActionModeChanged() {
        val isEditingDisplayName = this.displayNameEditActionMode != null

        binding.btnGroupNameDisplay.isInvisible = isEditingDisplayName
        binding.displayNameEditText.isInvisible = !isEditingDisplayName

        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingDisplayName) {
            binding.displayNameEditText.setText(binding.btnGroupNameDisplay.text)
            binding.displayNameEditText.selectAll()
            binding.displayNameEditText.requestFocus()
            inputMethodManager.showSoftInput(binding.displayNameEditText, 0)

            // Save the updated display name when the user presses enter on the soft keyboard
            binding.displayNameEditText.setOnEditorActionListener { v, actionId, event ->
                when (actionId) {
                    // Note: IME_ACTION_DONE is how we've configured the soft keyboard to respond,
                    // while IME_ACTION_UNSPECIFIED is what triggers when we hit enter on a
                    // physical keyboard.
                    EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_UNSPECIFIED -> {
                        saveDisplayName()
                        displayNameEditActionMode?.finish()
                        true
                    }
                    else -> false
                }
            }
        } else {
            inputMethodManager.hideSoftInputFromWindow(binding.displayNameEditText.windowToken, 0)
        }
    }

    private fun updateDisplayName(displayName: String): Boolean {
        binding.loader.isVisible = true

        // We'll assume we fail & flip the flag on success
        var updateWasSuccessful = false

        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(this@SettingsActivity);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot update display name - no network connection.")
        } else {
            // if we have a network connection then attempt to update the display name
            TextSecurePreferences.setProfileName(this, displayName)
            val user = configFactory.user
            if (user == null) {
                Log.w(TAG, "Cannot update display name - missing user details from configFactory.")
            } else {
                user.setName(displayName)
                binding.btnGroupNameDisplay.text = displayName
                updateWasSuccessful = true
            }
        }

        // Inform the user if we failed to update the display name
        if (!updateWasSuccessful) {
            Toast.makeText(this@SettingsActivity, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
        }

        binding.loader.isVisible = false
        return updateWasSuccessful
    }

    // Helper method used by updateProfilePicture and removeProfilePicture to sync it online
    private fun syncProfilePicture(profilePicture: ByteArray, onFail: () -> Unit) {
        binding.loader.isVisible = true

        // Grab the profile key and kick of the promise to update the profile picture
        val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this)
        val updateProfilePicturePromise = ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, this)

        // If the online portion of the update succeeded then update the local state
        updateProfilePicturePromise.successUi {

            // When removing the profile picture the supplied ByteArray is empty so we'll clear the local data
            if (profilePicture.isEmpty()) {
                MessagingModuleConfiguration.shared.storage.clearUserPic()
            }

            val userConfig = configFactory.user
            AvatarHelper.setAvatar(this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)!!), profilePicture)
            prefs.setProfileAvatarId(SecureRandom().nextInt() )
            ProfileKeyUtil.setEncodedProfileKey(this, encodedProfileKey)

            // Attempt to grab the details we require to update the profile picture
            val url = prefs.getProfilePictureURL()
            val profileKey = ProfileKeyUtil.getProfileKey(this)

            // If we have a URL and a profile key then set the user's profile picture
            if (!url.isNullOrEmpty() && profileKey.isNotEmpty()) {
                userConfig?.setPic(UserPic(url, profileKey))
            }

            if (userConfig != null && userConfig.needsDump()) {
                configFactory.persist(userConfig, SnodeAPI.nowWithOffset)
            }

            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@SettingsActivity)

            // Update our visuals
            binding.profilePictureView.recycle()
            binding.profilePictureView.update()
        }

        // If the sync failed then inform the user
        updateProfilePicturePromise.failUi { onFail() }

        // Finally, remove the loader animation after we've waited for the attempt to succeed or fail
        updateProfilePicturePromise.alwaysUi { binding.loader.isVisible = false }
    }

    private fun updateProfilePicture(profilePicture: ByteArray) {

        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(this@SettingsActivity);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot update profile picture - no network connection.")
            Toast.makeText(this@SettingsActivity, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when uploading profile picture.")
            Toast.makeText(this@SettingsActivity, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
        }

        syncProfilePicture(profilePicture, onFail)
    }

    private fun removeProfilePicture() {

        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(this@SettingsActivity);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot remove profile picture - no network connection.")
            Toast.makeText(this@SettingsActivity, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when removing profile picture.")
            Toast.makeText(this@SettingsActivity, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
        }

        val emptyProfilePicture = ByteArray(0)
        syncProfilePicture(emptyProfilePicture, onFail)
    }
    // endregion

    // region Interaction

    /**
     * @return true if the update was successful.
     */
    private fun saveDisplayName(): Boolean {
        val displayName = binding.displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            Toast.makeText(this, R.string.activity_settings_display_name_missing_error, Toast.LENGTH_SHORT).show()
            return false
        }
        if (displayName.toByteArray().size > ProfileManagerProtocol.NAME_PADDED_LENGTH) {
            Toast.makeText(this, R.string.activity_settings_display_name_too_long_error, Toast.LENGTH_SHORT).show()
            return false
        }
        return updateDisplayName(displayName)
    }

    private fun showEditProfilePictureUI() {
        showSessionDialog {
            title(R.string.activity_settings_set_display_picture)
            view(R.layout.dialog_change_avatar)
            button(R.string.activity_settings_upload) { startAvatarSelection() }
            if (prefs.getProfileAvatarId() != 0) {
                button(R.string.activity_settings_remove) { removeProfilePicture() }
            }
            cancelButton()
        }.apply {
            val profilePic = findViewById<ProfilePictureView>(R.id.profile_picture_view)
                ?.also(::setupProfilePictureView)

            val pictureIcon = findViewById<View>(R.id.ic_pictures)

            val recipient = Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false)

            val photoSet = (recipient.contactPhoto as ProfileContactPhoto).avatarObject !in setOf("0", "")

            profilePic?.isVisible = photoSet
            pictureIcon?.isVisible = !photoSet
        }
    }

    private fun startAvatarSelection() {
        // Ask for an optional camera permission.
        Permissions.with(this)
            .request(Manifest.permission.CAMERA)
            .onAnyResult {
                tempFile = AvatarSelection.startAvatarSelection(this, false, true)
            }
            .execute()
    }
    // endregion

    private inner class DisplayNameEditActionModeCallback: ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = getString(R.string.activity_settings_display_name_edit_text_hint)
            mode.menuInflater.inflate(R.menu.menu_apply, menu)
            this@SettingsActivity.displayNameEditActionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@SettingsActivity.displayNameEditActionMode = null
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.applyButton -> {
                    if (this@SettingsActivity.saveDisplayName()) {
                        mode.finish()
                    }
                    return true
                }
            }
            return false
        }
    }

    @Composable
    fun Buttons() {
        Column {
            Row(
                modifier = Modifier
                    .padding(horizontal = LocalDimensions.current.spacing)
                    .padding(top = LocalDimensions.current.xxsSpacing),
                horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing),
            ) {
                PrimaryOutlineButton(
                    stringResource(R.string.share),
                    modifier = Modifier.weight(1f),
                    onClick = ::sendInvitationToUseSession
                )

                PrimaryOutlineCopyButton(
                    modifier = Modifier.weight(1f),
                    onClick = ::copyPublicKey,
                )
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))

            val hasPaths by hasPaths().collectAsState(initial = false)

            Cell {
                Column {
                    Crossfade(if (hasPaths) R.drawable.ic_status else R.drawable.ic_path_yellow, label = "path") {
                        LargeItemButtonWithDrawable(R.string.activity_path_title, it) { show<PathActivity>() }
                    }
                    Divider()
                    LargeItemButton(R.string.activity_settings_privacy_button_title, R.drawable.ic_privacy_icon) { show<PrivacySettingsActivity>() }
                    Divider()
                    LargeItemButton(R.string.activity_settings_notifications_button_title, R.drawable.ic_speaker, Modifier.contentDescription(R.string.AccessibilityId_notifications)) { show<NotificationSettingsActivity>() }
                    Divider()
                    LargeItemButton(R.string.activity_settings_conversations_button_title, R.drawable.ic_conversations, Modifier.contentDescription(R.string.AccessibilityId_conversations)) { show<ChatSettingsActivity>() }
                    Divider()
                    LargeItemButton(R.string.activity_settings_message_requests_button_title, R.drawable.ic_message_requests, Modifier.contentDescription(R.string.AccessibilityId_message_requests)) { show<MessageRequestsActivity>() }
                    Divider()
                    LargeItemButton(R.string.activity_settings_message_appearance_button_title, R.drawable.ic_appearance, Modifier.contentDescription(R.string.AccessibilityId_appearance)) { show<AppearanceSettingsActivity>() }
                    Divider()
                    LargeItemButton(R.string.activity_settings_invite_button_title, R.drawable.ic_invite_friend, Modifier.contentDescription(R.string.AccessibilityId_invite_friend)) { sendInvitationToUseSession() }
                    Divider()
                    if (!prefs.getHidePassword()) {
                        LargeItemButton(R.string.sessionRecoveryPassword, R.drawable.ic_shield_outline, Modifier.contentDescription(R.string.AccessibilityId_recovery_password_menu_item)) { show<RecoveryPasswordActivity>() }
                        Divider()
                    }
                    LargeItemButton(R.string.activity_settings_help_button, R.drawable.ic_help, Modifier.contentDescription(R.string.AccessibilityId_help)) { show<HelpSettingsActivity>() }
                    Divider()
                    LargeItemButton(R.string.activity_settings_clear_all_data_button_title, R.drawable.ic_message_details__trash, Modifier.contentDescription(R.string.AccessibilityId_clear_data), dangerButtonColors()) { ClearAllDataDialog().show(supportFragmentManager, "Clear All Data Dialog") }
                }
            }
        }
    }
}

private fun Context.hasPaths(): Flow<Boolean> = LocalBroadcastManager.getInstance(this).hasPaths()
private fun LocalBroadcastManager.hasPaths(): Flow<Boolean> = callbackFlow {
    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { trySend(Unit) }
    }

    registerReceiver(receiver, IntentFilter("buildingPaths"))
    registerReceiver(receiver, IntentFilter("pathsBuilt"))

    awaitClose { unregisterReceiver(receiver) }
}.onStart { emit(Unit) }.map { OnionRequestAPI.paths.isNotEmpty() }