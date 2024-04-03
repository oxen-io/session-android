package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.DimenRes
import com.bumptech.glide.load.engine.DiskCacheStrategy
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewProfilePictureBinding
import org.session.libsession.avatars.ContactColors
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.avatars.ResourceContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests

class ProfilePictureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {
    private val binding = ViewProfilePictureBinding.inflate(LayoutInflater.from(context), this)
    private val glide: GlideRequests = GlideApp.with(this)
    var publicKey: String? = null
    var displayName: String? = null
    var additionalPublicKey: String? = null
    var additionalDisplayName: String? = null
    var isLarge = false

    private val profilePicturesCache = mutableMapOf<String, String?>()
    private val unknownRecipientDrawable by lazy { ResourceContactPhoto(R.drawable.ic_profile_default)
        .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false) }
    private val unknownOpenGroupDrawable by lazy { ResourceContactPhoto(R.drawable.ic_notification)
        .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false) }


    // endregion

    constructor(context: Context, sender: Recipient): this(context) {
        update(sender)
    }

    // region Updating
    fun update(recipient: Recipient) {
        fun getUserDisplayName(publicKey: String): String {
            val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(publicKey)
            return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
        }

        if (recipient.isClosedGroupRecipient) {
            val members = DatabaseComponent.get(context).groupDatabase()
                    .getGroupMemberAddresses(recipient.address.toGroupString(), true)
                    .sorted()
                    .take(2)
                    .toMutableList()
            if (members.size <= 1) {
                publicKey = ""
                displayName = ""
                additionalPublicKey = ""
                additionalDisplayName = ""
            } else {
                val pk = members.getOrNull(0)?.serialize() ?: ""
                publicKey = pk
                displayName = getUserDisplayName(pk)
                val apk = members.getOrNull(1)?.serialize() ?: ""
                additionalPublicKey = apk
                additionalDisplayName = getUserDisplayName(apk)
            }
        } else if(recipient.isOpenGroupInboxRecipient) {
            val publicKey = GroupUtil.getDecodedOpenGroupInboxSessionId(recipient.address.serialize())
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        } else {
            val publicKey = recipient.address.toString()
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        }
        update()
    }

    fun update() {
        val publicKey = publicKey ?: return
        val additionalPublicKey = additionalPublicKey
        if (additionalPublicKey != null) {
            setProfilePictureIfNeeded(binding.doubleModeImageView1, publicKey, displayName, R.dimen.small_profile_picture_size)
            setProfilePictureIfNeeded(binding.doubleModeImageView2, additionalPublicKey, additionalDisplayName, R.dimen.small_profile_picture_size)
            binding.doubleModeImageViewContainer.visibility = View.VISIBLE
        } else {
            glide.clear(binding.doubleModeImageView1)
            glide.clear(binding.doubleModeImageView2)
            binding.doubleModeImageViewContainer.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && !isLarge) {
            setProfilePictureIfNeeded(binding.singleModeImageView, publicKey, displayName, R.dimen.medium_profile_picture_size)
            binding.singleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.singleModeImageView)
            binding.singleModeImageView.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && isLarge) {
            setProfilePictureIfNeeded(binding.largeSingleModeImageView, publicKey, displayName, R.dimen.large_profile_picture_size)
            binding.largeSingleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.largeSingleModeImageView)
            binding.largeSingleModeImageView.visibility = View.INVISIBLE
        }
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?, @DimenRes sizeResId: Int) {
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            if (profilePicturesCache.containsKey(publicKey) && profilePicturesCache[publicKey] == recipient.profileAvatar) return
            val signalProfilePicture = recipient.contactPhoto
            val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject

            val placeholder = PlaceholderAvatarPhoto(context, publicKey, displayName ?: "${publicKey.take(4)}...${publicKey.takeLast(4)}")

            if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                glide.clear(imageView)
                glide.load(signalProfilePicture)
                    .placeholder(unknownRecipientDrawable)
                    .centerCrop()
                    .error(glide.load(placeholder))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(imageView)
            } else if (recipient.isOpenGroupRecipient && recipient.groupAvatarId == null) {
                glide.clear(imageView)
                glide.load(unknownOpenGroupDrawable)
                    .centerCrop()
                    .circleCrop()
                    .into(imageView)
            } else {
                glide.clear(imageView)
                glide.load(placeholder)
                    .placeholder(unknownRecipientDrawable)
                    .centerCrop()
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE).circleCrop().into(imageView)
            }
            profilePicturesCache[publicKey] = recipient.profileAvatar
        } else {
            glide.load(unknownRecipientDrawable)
                .centerCrop()
                .into(imageView)
        }
    }

    fun recycle() {
        profilePicturesCache.clear()
    }
    // endregion
}
