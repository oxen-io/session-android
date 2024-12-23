package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.ProfilePictureUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.ExternalStorageUtil.getImageDir
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.NoExternalStorageException
import org.session.libsignal.utilities.Util.SECURE_RANDOM
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.preferences.SettingsViewModel.AvatarDialogState.TempAvatar
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.NetworkUtils
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory
) : ViewModel() {
    private val TAG = "SettingsViewModel"

    private var tempFile: File? = null

    val hexEncodedPublicKey: String = prefs.getLocalNumber() ?: ""

    private val userAddress = Address.fromSerialized(hexEncodedPublicKey)

    private val _avatarDialogState: MutableStateFlow<AvatarDialogState> = MutableStateFlow(
        getDefaultAvatarDialogState()
    )
    val avatarDialogState: StateFlow<AvatarDialogState>
        get() = _avatarDialogState

    private val _showLoader: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLoader: StateFlow<Boolean>
        get() = _showLoader

    private val _recoveryHidden: MutableStateFlow<Boolean> = MutableStateFlow(prefs.getHidePassword())
    val recoveryHidden: StateFlow<Boolean>
        get() = _recoveryHidden

    private val _avatarData: MutableStateFlow<AvatarData?> = MutableStateFlow(null)
    val avatarData: StateFlow<AvatarData?>
        get() = _avatarData

    /**
     * Refreshes the avatar on the main settings page
     */
    private val _refreshAvatar: MutableSharedFlow<Unit> = MutableSharedFlow()
    val refreshAvatar: SharedFlow<Unit>
        get() = _refreshAvatar.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val recipient = Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false)
            _avatarData.update {
                AvatarData(
                    publicKey = hexEncodedPublicKey,
                    displayName = getDisplayName(),
                    recipient = recipient
                )
            }
        }
    }

    fun getDisplayName(): String =
        prefs.getProfileName() ?: truncateIdForDisplay(hexEncodedPublicKey)

    fun hasAvatar() = prefs.getProfileAvatarId() != 0

    fun createTempFile(): File? {
        try {
            tempFile = File.createTempFile("avatar-capture", ".jpg", getImageDir(context))
        } catch (e: IOException) {
            Log.e("Cannot reserve a temporary avatar capture file.", e)
        } catch (e: NoExternalStorageException) {
            Log.e("Cannot reserve a temporary avatar capture file.", e)
        }

        return tempFile
    }

    fun getTempFile() = tempFile

    fun getUser() = configFactory.user

    fun onAvatarPicked(result: CropImageView.CropResult) {
        when {
            result.isSuccessful -> {
                Log.i(TAG, result.getUriFilePath(context).toString())

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val profilePictureToBeUploaded =
                            BitmapUtil.createScaledBytes(
                                context,
                                result.getUriFilePath(context).toString(),
                                ProfileMediaConstraints()
                            ).bitmap

                        // update dialog with temporary avatar (has not been saved/uploaded yet)
                        _avatarDialogState.value =
                            AvatarDialogState.TempAvatar(profilePictureToBeUploaded, hasAvatar())
                    } catch (e: BitmapDecodingException) {
                        Log.e(TAG, e)
                    }
                }
            }

            result is CropImage.CancelledResult -> {
                Log.i(TAG, "Cropping image was cancelled by the user")
            }

            else -> {
                Log.e(TAG, "Cropping image failed")
            }
        }
    }

    fun onAvatarDialogDismissed() {
        _avatarDialogState.value = getDefaultAvatarDialogState()
    }

    fun getDefaultAvatarDialogState() = if (hasAvatar()) AvatarDialogState.UserAvatar(userAddress)
    else AvatarDialogState.NoAvatar

    fun saveAvatar() {
        val tempAvatar = (avatarDialogState.value as? TempAvatar)?.data
            ?: return Toast.makeText(context, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()

        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(context);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot update profile picture - no network connection.")
            Toast.makeText(context, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when uploading profile picture.")
            Toast.makeText(context, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
        }

        syncProfilePicture(tempAvatar, onFail)
    }


    fun removeAvatar() {
        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(context);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot remove profile picture - no network connection.")
            Toast.makeText(context, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when removing profile picture.")
            Toast.makeText(context, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
        }

        val emptyProfilePicture = ByteArray(0)
        syncProfilePicture(emptyProfilePicture, onFail)
    }

    // Helper method used by updateProfilePicture and removeProfilePicture to sync it online
    private fun syncProfilePicture(profilePicture: ByteArray, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _showLoader.value = true

            try {
                // Grab the profile key and kick of the promise to update the profile picture
                val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(context)
                ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, context)

                // If the online portion of the update succeeded then update the local state
                val userConfig = configFactory.user
                AvatarHelper.setAvatar(
                    context,
                    Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)!!),
                    profilePicture
                )

                // When removing the profile picture the supplied ByteArray is empty so we'll clear the local data
                if (profilePicture.isEmpty()) {
                    MessagingModuleConfiguration.shared.storage.clearUserPic()

                    // update dialog state
                    _avatarDialogState.value = AvatarDialogState.NoAvatar
                } else {
                    prefs.setProfileAvatarId(SECURE_RANDOM.nextInt())
                    ProfileKeyUtil.setEncodedProfileKey(context, encodedProfileKey)

                    // Attempt to grab the details we require to update the profile picture
                    val url = prefs.getProfilePictureURL()
                    val profileKey = ProfileKeyUtil.getProfileKey(context)

                    // If we have a URL and a profile key then set the user's profile picture
                    if (!url.isNullOrEmpty() && profileKey.isNotEmpty()) {
                        userConfig?.setPic(UserPic(url, profileKey))
                    }

                    // update dialog state
                    _avatarDialogState.value = AvatarDialogState.UserAvatar(userAddress)
                }

                if (userConfig != null && userConfig.needsDump()) {
                    configFactory.persist(userConfig, SnodeAPI.nowWithOffset)
                }

                ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
            } catch (e: Exception){ // If the sync failed then inform the user
                Log.d(TAG, "Error syncing avatar: $e")
                withContext(Dispatchers.Main) {
                    onFail()
                }
            }

            // Finally update the main avatar
            _refreshAvatar.emit(Unit)
            // And remove the loader animation after we've waited for the attempt to succeed or fail
            _showLoader.value = false
        }
    }

    fun permanentlyHidePassword() {
        //todo we can simplify this once we expose all our sharedPrefs as flows
        prefs.setHidePassword(true)
        _recoveryHidden.update { true }
    }

    sealed class AvatarDialogState() {
        object NoAvatar : AvatarDialogState()
        data class UserAvatar(val address: Address) : AvatarDialogState()
        data class TempAvatar(
            val data: ByteArray,
            val hasAvatar: Boolean // true if the user has an avatar set already but is in this temp state because they are trying out a new avatar
        ) : AvatarDialogState()
    }

    data class AvatarData(
        val publicKey: String,
        val displayName: String,
        val recipient: Recipient
    )
}