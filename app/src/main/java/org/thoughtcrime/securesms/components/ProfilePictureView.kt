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
    private fun loadAsDoubleImages(groupAddress: Address, knownRecipient: Recipient?, allowMemoryCache: Boolean) {
        cancelLastLoadJob()

        // The use of GlobalScope is intentional here, as there is no better lifecycle scope that we can use
        // to launch a coroutine from a view. The potential memory leak is not a concern here, as
        // the coroutine is very short-lived. If you change the code here to be long live then you'll
        // need to find a better scope to launch the coroutine from.
        lastLoadJob = GlobalScope.launch(Dispatchers.Main) {
            // Load group avatar if available, otherwise load member avatars
            val groupAvatarOrMemberAvatars = withContext(Dispatchers.Default) {
                (knownRecipient ?: Recipient.from(context, groupAddress, false)).contactPhoto
                    ?: groupDatabase.getGroupMembers(groupAddress.toGroupString(), true)
                        .takeIf { it.size >= 2 }
            }

            when (groupAvatarOrMemberAvatars) {
                is ContactPhoto -> {
                    setShowAsDoubleMode(false)
                    Glide.with(this@ProfilePictureView)
                        .load(groupAvatarOrMemberAvatars)
                        .error(unknownRecipientDrawable)
                        .circleCrop()
                        .skipMemoryCache(!allowMemoryCache)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.singleModeImageView)
                }

                is List<*> -> {
                    val (first, second) = groupAvatarOrMemberAvatars.filterIsInstance<Recipient>()
                    setShowAsDoubleMode(true)
                    Glide.with(this@ProfilePictureView)
                        .load(first)
                        .error(PlaceholderAvatarPhoto(first.address.serialize(), first.profileName.orEmpty()))
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(!allowMemoryCache)
                        .into(binding.doubleModeImageView1)

                    Glide.with(this@ProfilePictureView)
                        .load(second)
                        .error(PlaceholderAvatarPhoto(second.address.serialize(), second.profileName.orEmpty()))
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(!allowMemoryCache)
                        .into(binding.doubleModeImageView2)
                }

                else -> {
                    setShowAsDoubleMode(false)
                    binding.singleModeImageView.setImageDrawable(unknownRecipientDrawable)
                }
            }
        }
    }

    private fun loadAsSingleImage(address: Address, allowMemoryCache: Boolean) {
        cancelLastLoadJob()

        setShowAsDoubleMode(false)

        val errorModel: Any = when {
            address.isCommunity -> unknownOpenGroupDrawable
            address.isContact -> PlaceholderAvatarPhoto(address.serialize(), null)
            else -> unknownRecipientDrawable
        }

        Glide.with(this)
            .load(address)
            .error(errorModel)
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(!allowMemoryCache)
            .into(binding.singleModeImageView)
    }

    fun load(recipient: Recipient, allowMemoryCache: Boolean = true) {
        if (recipient.isClosedGroupRecipient) {
            loadAsDoubleImages(recipient.address, recipient, allowMemoryCache)
        } else {
            loadAsSingleImage(recipient.address, allowMemoryCache)
        }
    }

    fun load(address: Address, allowMemoryCache: Boolean = true) {
        if (address.isClosedGroup) {
            loadAsDoubleImages(address, knownRecipient = null, allowMemoryCache)
        } else {
            loadAsSingleImage(address, allowMemoryCache)
        }
    }
}