package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.canhub.cropper.CropImageContract
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivitySettingsBinding
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol
import org.session.libsession.utilities.StringSubstitutionConstants.VERSION_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.avatar.AvatarSelection
import org.thoughtcrime.securesms.debugmenu.DebugActivity
import org.thoughtcrime.securesms.home.PathActivity
import org.thoughtcrime.securesms.messagerequests.MessageRequestsActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.SettingsViewModel.AvatarDialogState.NoAvatar
import org.thoughtcrime.securesms.preferences.SettingsViewModel.AvatarDialogState.TempAvatar
import org.thoughtcrime.securesms.preferences.SettingsViewModel.AvatarDialogState.UserAvatar
import org.thoughtcrime.securesms.preferences.appearance.AppearanceSettingsActivity
import org.thoughtcrime.securesms.recoverypassword.RecoveryPasswordActivity
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.Avatar
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LargeItemButton
import org.thoughtcrime.securesms.ui.LargeItemButtonWithDrawable
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineCopyButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.setThemedContent
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.dangerButtonColors
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.NetworkUtils
import org.thoughtcrime.securesms.util.push
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : PassphraseRequiredActionBarActivity() {
    private val TAG = "SettingsActivity"

    @Inject
    lateinit var prefs: TextSecurePreferences

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var binding: ActivitySettingsBinding
    private var displayNameEditActionMode: ActionMode? = null
        set(value) { field = value; handleDisplayNameEditActionModeChanged() }

    private val onAvatarCropped = registerForActivityResult(CropImageContract()) { result ->
        viewModel.onAvatarPicked(result)
    }

    private val onPickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){ result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val outputFile = Uri.fromFile(File(cacheDir, "cropped"))
        val inputFile: Uri? = result.data?.data ?: viewModel.getTempFile()?.let(Uri::fromFile)
        cropImage(inputFile, outputFile)
    }

    private val hideRecoveryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        if(result.data?.getBooleanExtra(RecoveryPasswordActivity.RESULT_RECOVERY_HIDDEN, false) == true){
            viewModel.permanentlyHidePassword()
        }
    }

    private val avatarSelection = AvatarSelection(this, onAvatarCropped, onPickImage)

    private var showAvatarDialog: Boolean by mutableStateOf(false)

    companion object {
        private const val SCROLL_STATE = "SCROLL_STATE"
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // set the toolbar icon to a close icon
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_close_24)

        // set the compose dialog content
        binding.avatarDialog.setThemedContent {
            if(showAvatarDialog){
                AvatarDialogContainer(
                    saveAvatar = viewModel::saveAvatar,
                    removeAvatar = viewModel::removeAvatar,
                    startAvatarSelection = ::startAvatarSelection
                )
            }
        }

        binding.run {
            profilePictureView.setOnClickListener {
                binding.avatarDialog.isVisible = true
                showAvatarDialog = true
            }
            ctnGroupNameSection.setOnClickListener { startActionMode(DisplayNameEditActionModeCallback()) }
            btnGroupNameDisplay.text = viewModel.getDisplayName()
            publicKeyTextView.text = viewModel.hexEncodedPublicKey
            val gitCommitFirstSixChars = BuildConfig.GIT_HASH.take(6)
            val environment: String = if(BuildConfig.BUILD_TYPE == "release") "" else " - ${prefs.getEnvironment().label}"
            val versionDetails = " ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE} - $gitCommitFirstSixChars) $environment"
            val versionString = Phrase.from(applicationContext, R.string.updateVersion).put(VERSION_KEY, versionDetails).format()
            versionTextView.text = versionString
        }

        binding.composeView.setThemedContent {
            val recoveryHidden by viewModel.recoveryHidden.collectAsState()
            Buttons(recoveryHidden = recoveryHidden)
        }

        lifecycleScope.launch {
            viewModel.showLoader.collect {
                binding.loader.isVisible = it
            }
        }

        lifecycleScope.launch {
            viewModel.refreshAvatar.collect {
                binding.profilePictureView.recycle()
                binding.profilePictureView.update()
            }
        }

        lifecycleScope.launch {
            viewModel.avatarData.collect {
                if(it == null) return@collect

                binding.profilePictureView.apply {
                    publicKey = it.publicKey
                    displayName = it.displayName
                    update(it.recipient)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        binding.profilePictureView.update()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_bottom)
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
        if (BuildConfig.DEBUG) {
            menu.findItem(R.id.action_qr_code)?.contentDescription = resources.getString(R.string.AccessibilityId_qrView)
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
            val user = viewModel.getUser()
            if (user == null) {
                Log.w(TAG, "Cannot update display name - missing user details from configFactory.")
            } else {
                user.setName(displayName)
                // sync remote config
                ConfigurationMessageUtilities.syncConfigurationIfNeeded(this)
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
    // endregion

    // region Interaction

    /**
     * @return true if the update was successful.
     */
    private fun saveDisplayName(): Boolean {
        val displayName = binding.displayNameEditText.text.toString().trim()

        if (displayName.isEmpty()) {
            Toast.makeText(this, R.string.displayNameErrorDescription, Toast.LENGTH_SHORT).show()
            return false
        }

        if (displayName.toByteArray().size > ProfileManagerProtocol.NAME_PADDED_LENGTH) {
            Toast.makeText(this, R.string.displayNameErrorDescriptionShorter, Toast.LENGTH_SHORT).show()
            return false
        }

        return updateDisplayName(displayName)
    }

    private fun startAvatarSelection() {
        // Ask for an optional camera permission.
        Permissions.with(this)
            .request(Manifest.permission.CAMERA)
            .onAnyResult {
                avatarSelection.startAvatarSelection(
                    includeClear = false,
                    attemptToIncludeCamera = true,
                    createTempFile = viewModel::createTempFile
                )
            }
            .execute()
    }

    private fun cropImage(inputFile: Uri?, outputFile: Uri?){
        avatarSelection.circularCropImage(
            inputFile = inputFile,
            outputFile = outputFile,
        )
    }
    // endregion

    private inner class DisplayNameEditActionModeCallback: ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = getString(R.string.displayNameEnter)
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
    fun Buttons(
        recoveryHidden: Boolean
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = LocalDimensions.current.spacing)
        ) {
            Row(
                modifier = Modifier
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
                    // add the debug menu in non release builds
                    if (BuildConfig.BUILD_TYPE != "release") {
                        LargeItemButton("Debug Menu", R.drawable.ic_settings) { push<DebugActivity>() }
                        Divider()
                    }

                    Crossfade(if (hasPaths) R.drawable.ic_status else R.drawable.ic_path_yellow, label = "path") {
                        LargeItemButtonWithDrawable(R.string.onionRoutingPath, it) { push<PathActivity>() }
                    }
                    Divider()

                    LargeItemButton(R.string.sessionPrivacy, R.drawable.ic_privacy_icon) { push<PrivacySettingsActivity>() }
                    Divider()

                    LargeItemButton(R.string.sessionNotifications, R.drawable.ic_speaker, Modifier.contentDescription(R.string.AccessibilityId_notifications)) { push<NotificationSettingsActivity>() }
                    Divider()

                    LargeItemButton(R.string.sessionConversations, R.drawable.ic_conversations, Modifier.contentDescription(R.string.AccessibilityId_sessionConversations)) { push<ChatSettingsActivity>() }
                    Divider()

                    LargeItemButton(R.string.sessionMessageRequests, R.drawable.ic_message_requests, Modifier.contentDescription(R.string.AccessibilityId_sessionMessageRequests)) { push<MessageRequestsActivity>() }
                    Divider()

                    LargeItemButton(R.string.sessionAppearance, R.drawable.ic_appearance, Modifier.contentDescription(R.string.AccessibilityId_sessionAppearance)) { push<AppearanceSettingsActivity>() }
                    Divider()

                    LargeItemButton(
                        R.string.sessionInviteAFriend,
                        R.drawable.ic_invite_friend,
                        Modifier.contentDescription(R.string.AccessibilityId_sessionInviteAFriend)
                    ) { sendInvitationToUseSession() }
                    Divider()

                    // Only show the recovery password option if the user has not chosen to permanently hide it
                    if (!recoveryHidden) {
                        LargeItemButton(
                            R.string.sessionRecoveryPassword,
                            R.drawable.ic_shield_outline,
                            Modifier.contentDescription(R.string.AccessibilityId_sessionRecoveryPasswordMenuItem)
                        ) {
                            hideRecoveryLauncher.launch(Intent(baseContext, RecoveryPasswordActivity::class.java))
                            overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
                        }
                        Divider()
                    }

                    LargeItemButton(R.string.sessionHelp, R.drawable.ic_help, Modifier.contentDescription(R.string.AccessibilityId_help)) { push<HelpSettingsActivity>() }
                    Divider()

                    LargeItemButton(R.string.sessionClearData,
                        R.drawable.ic_delete,
                        Modifier.contentDescription(R.string.AccessibilityId_sessionClearData),
                        dangerButtonColors()
                    ) { ClearAllDataDialog().show(supportFragmentManager, "Clear All Data Dialog") }
                }
            }
        }
    }

    @Composable
    fun AvatarDialogContainer(
        startAvatarSelection: ()->Unit,
        saveAvatar: ()->Unit,
        removeAvatar: ()->Unit
    ){
        val state by viewModel.avatarDialogState.collectAsState()

        AvatarDialog(
            state = state,
            startAvatarSelection = startAvatarSelection,
            saveAvatar = saveAvatar,
            removeAvatar = removeAvatar
        )
    }

    @Composable
    fun AvatarDialog(
        state: SettingsViewModel.AvatarDialogState,
        startAvatarSelection: ()->Unit,
        saveAvatar: ()->Unit,
        removeAvatar: ()->Unit
    ){
        AlertDialog(
            onDismissRequest = {
                viewModel.onAvatarDialogDismissed()
                showAvatarDialog = false
            },
            title = stringResource(R.string.profileDisplayPictureSet),
            content = {
                // custom content that has the displayed images

                // main container that control the overall size and adds the rounded bg
                Box(
                    modifier = Modifier
                        .padding(top = LocalDimensions.current.smallSpacing)
                        .size(dimensionResource(id = R.dimen.large_profile_picture_size))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // the ripple doesn't look nice as a square with the plus icon on top too
                        ) {
                            startAvatarSelection()
                        }
                        .qaTag(stringResource(R.string.AccessibilityId_avatarPicker))
                        .background(
                            shape = CircleShape,
                            color = LocalColors.current.backgroundBubbleReceived,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // the image content will depend on state type
                    when(val s = state){
                        // user avatar
                        is UserAvatar -> {
                            Avatar(userAddress = s.address)
                        }

                        // temporary image
                        is TempAvatar -> {
                            Image(
                                modifier = Modifier.size(dimensionResource(id = R.dimen.large_profile_picture_size))
                                    .clip(shape = CircleShape,),
                                bitmap = BitmapFactory.decodeByteArray(s.data, 0, s.data.size).asImageBitmap(),
                                contentDescription = null
                            )
                        }

                        // empty state
                        else -> {
                            Image(
                                modifier = Modifier.align(Alignment.Center),
                                painter = painterResource(id = R.drawable.ic_pictures),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(LocalColors.current.textSecondary)
                            )
                        }
                    }

                    // '+' button that sits atop the custom content
                    Image(
                        modifier = Modifier
                            .size(LocalDimensions.current.spacing)
                            .background(
                                shape = CircleShape,
                                color = LocalColors.current.primary
                            )
                            .padding(LocalDimensions.current.xxxsSpacing)
                            .align(Alignment.BottomEnd)
                        ,
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.Black)
                    )
                }
            },
            showCloseButton = true, // display the 'x' button
            buttons = listOf(
                DialogButtonModel(
                    text = GetString(R.string.save),
                    enabled = state is TempAvatar,
                    onClick = saveAvatar
                ),
                DialogButtonModel(
                    text = GetString(R.string.remove),
                    color = LocalColors.current.danger,
                    enabled = state is UserAvatar || // can remove is the user has an avatar set
                            (state is TempAvatar && state.hasAvatar),
                    onClick = removeAvatar
                )
            )
        )
    }

    @Preview
    @Composable
    fun PreviewAvatarDialog(
        @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
    ){
        PreviewTheme(colors) {
            AvatarDialog(
                state = NoAvatar,
                startAvatarSelection = {},
                saveAvatar = {},
                removeAvatar = {}
            )
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