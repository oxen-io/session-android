package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewProfilePictureBinding
import org.session.libsession.avatars.ContactColors
import org.session.libsession.avatars.ContactPhoto
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsession.avatars.ResourceContactPhoto
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import javax.inject.Inject

@AndroidEntryPoint
class ProfilePictureViewV2 : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    @Inject
    lateinit var groupDatabase: GroupDatabase

    private val binding = ViewProfilePictureBinding.inflate(LayoutInflater.from(context), this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var lastLoadJob: Job? = null

    private val unknownRecipientDrawable by lazy(LazyThreadSafetyMode.NONE) {
        ResourceContactPhoto(R.drawable.ic_profile_default)
            .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false)
    }

    private val unknownOpenGroupDrawable by lazy(LazyThreadSafetyMode.NONE) {
        ResourceContactPhoto(R.drawable.ic_notification)
            .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false)
    }

    private fun setShowAsDoubleMode(showAsDouble: Boolean) {
        binding.doubleModeImageViewContainer.isVisible = showAsDouble
        binding.singleModeImageView.isVisible = !showAsDouble
    }

    private fun loadAsDoubleImages(groupAddress: Address, knownRecipient: Recipient?) {
        lastLoadJob?.cancel()

        lastLoadJob = scope.launch {
            // Load group avatar if available, otherwise load member avatars
            val groupAvatarOrMemberAvatars = withContext(Dispatchers.Default) {
                (knownRecipient ?: Recipient.from(context, groupAddress, false)).contactPhoto
                    ?: groupDatabase.getGroupMembers(groupAddress.toGroupString(), true)
                        .map { it.contactPhoto }
                        .takeIf { it.size >= 2 }
            }

            when (groupAvatarOrMemberAvatars) {
                is ContactPhoto -> {
                    setShowAsDoubleMode(false)
                    Glide.with(this@ProfilePictureViewV2)
                        .load(groupAvatarOrMemberAvatars)
                        .error(unknownRecipientDrawable)
                        .into(binding.singleModeImageView)
                }

                is List<*> -> {
                    val (first, second) = groupAvatarOrMemberAvatars.filterIsInstance<Recipient>()
                    setShowAsDoubleMode(true)
                    Glide.with(this@ProfilePictureViewV2)
                        .load(first)
                        .error(PlaceholderAvatarPhoto(first.address.serialize(), first.profileName.orEmpty()))
                        .into(binding.doubleModeImageView1)

                    Glide.with(this@ProfilePictureViewV2)
                        .load(second)
                        .error(PlaceholderAvatarPhoto(second.address.serialize(), second.profileName.orEmpty()))
                        .into(binding.doubleModeImageView2)
                }

                else -> {
                    setShowAsDoubleMode(false)
                    binding.singleModeImageView.setImageDrawable(unknownRecipientDrawable)
                }
            }
        }
    }

    private fun loadAsSingleImage(address: Address, knownDisplayName: String? = null) {
        lastLoadJob?.cancel()

        val error: Any = when {
            address.isCommunity -> unknownOpenGroupDrawable
            address.isContact -> PlaceholderAvatarPhoto(address.serialize(), knownDisplayName)
            else -> unknownRecipientDrawable
        }

        setShowAsDoubleMode(false)
        Glide.with(this)
            .load(address)
            .error(error)
            .into(binding.singleModeImageView)
    }

    fun load(recipient: Recipient) {
        if (recipient.isClosedGroupRecipient) {
            loadAsDoubleImages(recipient.address, recipient)
        } else {
            loadAsSingleImage(recipient.address)
        }
    }

    fun load(address: Address) {
        if (address.isClosedGroup) {
            loadAsDoubleImages(address, knownRecipient = null)
        } else {
            loadAsSingleImage(address)
        }
    }
}