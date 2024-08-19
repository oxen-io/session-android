package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewProfilePictureBinding
import org.session.libsession.avatars.ContactColors
import org.session.libsession.avatars.ContactPhoto
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsession.avatars.ResourceContactPhoto
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import javax.inject.Inject

@AndroidEntryPoint
class ProfilePictureView : FrameLayout {
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
    private var lastLoadJob: Job? = null
    private var lastLoadAddress: Address? = null

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

    private fun cancelLastLoadJob() {
        lastLoadJob?.cancel()
        lastLoadJob = null
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun loadAsDoubleImages(model: LoadModel) {
        cancelLastLoadJob()

        // The use of GlobalScope is intentional here, as there is no better lifecycle scope that we can use
        // to launch a coroutine from a view. The potential memory leak is not a concern here, as
        // the coroutine is very short-lived. If you change the code here to be long live then you'll
        // need to find a better scope to launch the coroutine from.
        lastLoadJob = GlobalScope.launch(Dispatchers.Main) {
            data class GroupMemberInfo(
                val contactPhoto: ContactPhoto?,
                val placeholderAvatarPhoto: PlaceholderAvatarPhoto,
            )

            // Load group avatar if available, otherwise load member avatars
            val groupAvatarOrMemberAvatars = withContext(Dispatchers.Default) {
                model.loadRecipient(context).contactPhoto
                    ?: groupDatabase.getGroupMembers(model.address.toGroupString(), true)
                        .map {
                            GroupMemberInfo(
                                contactPhoto = it.contactPhoto,
                                placeholderAvatarPhoto = PlaceholderAvatarPhoto(
                                    hashString = it.address.serialize(),
                                    displayName = it.displayName()
                                )
                            )
                        }
            }

            when (groupAvatarOrMemberAvatars) {
                is ContactPhoto -> {
                    setShowAsDoubleMode(false)
                    Glide.with(this@ProfilePictureView)
                        .load(groupAvatarOrMemberAvatars)
                        .error(unknownRecipientDrawable)
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.singleModeImageView)
                }

                is List<*> -> {
                    val first = groupAvatarOrMemberAvatars.getOrNull(0) as? GroupMemberInfo
                    val second = groupAvatarOrMemberAvatars.getOrNull(1) as? GroupMemberInfo
                    setShowAsDoubleMode(true)
                    Glide.with(binding.doubleModeImageView1)
                        .load(first?.let { it.contactPhoto ?: it.placeholderAvatarPhoto })
                        .error(first?.placeholderAvatarPhoto ?: unknownRecipientDrawable)
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.doubleModeImageView1)

                    Glide.with(binding.doubleModeImageView2)
                        .load(second?.let { it.contactPhoto ?: it.placeholderAvatarPhoto })
                        .error(second?.placeholderAvatarPhoto ?: unknownRecipientDrawable)
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.doubleModeImageView2)
                }

                else -> {
                    setShowAsDoubleMode(false)
                    binding.singleModeImageView.setImageDrawable(unknownRecipientDrawable)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun loadAsSingleImage(model: LoadModel) {
        cancelLastLoadJob()

        setShowAsDoubleMode(false)

        // Only clear the old image if the address has changed. This is important as we have a delay
        // in loading the image, if this view is reused for another address before the image is loaded,
        // the previous image could be displayed for a short period of time. We would want to avoid
        // displaying the wrong image, even for a short time.
        // However, if we are displaying the same user's image again, it's ok to show the old
        // image until the new one is loaded. This is a trade-off between performance and correctness.
        if (lastLoadAddress != model.address) {
            Glide.with(this).clear(this)
        }

        // The use of GlobalScope is intentional here, as there is no better lifecycle scope that we can use
        // to launch a coroutine from a view. The potential memory leak is not a concern here, as
        // the coroutine is very short-lived. If you change the code here to be long live then you'll
        // need to find a better scope to launch the coroutine from.
        lastLoadJob = GlobalScope.launch(Dispatchers.Main) {
            val (contactPhoto, avatarPlaceholder) = withContext(Dispatchers.Default) {
                model.loadRecipient(context).let {
                    it.contactPhoto to PlaceholderAvatarPhoto(it.address.serialize(), it.displayName())
                }
            }

            val address = model.address

            val errorModel: Any = when {
                address.isCommunity -> unknownOpenGroupDrawable
                address.isContact -> avatarPlaceholder
                else -> unknownRecipientDrawable
            }

            Glide.with(this@ProfilePictureView)
                .load(contactPhoto)
                .error(errorModel)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(binding.singleModeImageView)
        }
    }

    fun load(recipient: Recipient) {
        if (recipient.address.isClosedGroup) {
            loadAsDoubleImages(LoadModel.RecipientModel(recipient))
        } else {
            loadAsSingleImage(LoadModel.RecipientModel(recipient))
        }

        lastLoadAddress = recipient.address
    }

    fun load(address: Address) {
        if (address.isClosedGroup) {
            loadAsDoubleImages(LoadModel.AddressModel(address))
        } else {
            loadAsSingleImage(LoadModel.AddressModel(address))
        }

        lastLoadAddress = address
    }

    private fun Recipient.displayName(): String {
        return if (isLocalNumber) {
            TextSecurePreferences.getProfileName(context).orEmpty()
        } else {
            profileName ?: name ?: ""
        }
    }

    private sealed interface LoadModel {
        val address: Address

        /**
         * Load the recipient if it's not already loaded.
         */
        fun loadRecipient(context: Context): Recipient

        data class AddressModel(override val address: Address) : LoadModel {
            override fun loadRecipient(context: Context): Recipient {
                return Recipient.from(context, address, false)
            }
        }

        data class RecipientModel(val recipient: Recipient) : LoadModel {
            override val address: Address
                get() = recipient.address

            override fun loadRecipient(context: Context): Recipient = recipient
        }
    }
}