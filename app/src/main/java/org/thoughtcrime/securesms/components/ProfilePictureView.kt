package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bumptech.glide.load.engine.DiskCacheStrategy
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewProfilePictureBinding
import org.session.libsession.avatars.ContactColors
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.avatars.ResourceContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.AppTextSecurePreferences
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
    private val prefs = AppTextSecurePreferences(context)
    private val userPublicKey = prefs.getLocalNumber()
    var publicKey: String? = null
    var displayName: String? = null
    var additionalPublicKey: String? = null
    var additionalDisplayName: String? = null
    var isLarge = false

    private val profilePicturesCache = mutableMapOf<View, Recipient>()
    private val unknownRecipientDrawable by lazy { ResourceContactPhoto(R.drawable.ic_profile_default)
        .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false) }
    private val unknownOpenGroupDrawable by lazy { ResourceContactPhoto(R.drawable.ic_notification)
        .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false) }

    constructor(context: Context, sender: Recipient): this(context) {
        update(sender)
    }

    fun update(recipient: Recipient) {
        recipient.run { update(address, isClosedGroupRecipient, isOpenGroupInboxRecipient) }
    }

    fun update(
        address: Address,
        isClosedGroupRecipient: Boolean = false,
        isOpenGroupInboxRecipient: Boolean = false
    ) {
        fun getUserDisplayName(publicKey: String): String = prefs.takeIf { userPublicKey == publicKey }?.getProfileName()
            ?: DatabaseComponent.get(context).sessionContactDatabase().getContactWithAccountID(publicKey)?.displayName(Contact.ContactContext.REGULAR)
            ?: publicKey

        if (isClosedGroupRecipient) {
            val members = DatabaseComponent.get(context).groupDatabase()
                .getGroupMemberAddresses(address.toGroupString(), true)
                .sorted()
                .take(2)
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
        } else if(isOpenGroupInboxRecipient) {
            val publicKey = GroupUtil.getDecodedOpenGroupInboxAccountId(address.serialize())
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        } else {
            val publicKey = address.serialize()
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
            setProfilePictureIfNeeded(binding.doubleModeImageView1, publicKey, displayName)
            setProfilePictureIfNeeded(binding.doubleModeImageView2, additionalPublicKey, additionalDisplayName)
            binding.doubleModeImageViewContainer.visibility = View.VISIBLE
        } else {
            glide.clear(binding.doubleModeImageView1)
            glide.clear(binding.doubleModeImageView2)
            binding.doubleModeImageViewContainer.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && !isLarge) {
            setProfilePictureIfNeeded(binding.singleModeImageView, publicKey, displayName)
            binding.singleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.singleModeImageView)
            binding.singleModeImageView.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && isLarge) {
            setProfilePictureIfNeeded(binding.largeSingleModeImageView, publicKey, displayName)
            binding.largeSingleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.largeSingleModeImageView)
            binding.largeSingleModeImageView.visibility = View.INVISIBLE
        }
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?) {
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            if (profilePicturesCache[imageView] == recipient) return
            profilePicturesCache[imageView] = recipient
            val signalProfilePicture = recipient.contactPhoto
            val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject

            glide.clear(imageView)

            val placeholder = PlaceholderAvatarPhoto(publicKey, displayName ?: "${publicKey.take(4)}...${publicKey.takeLast(4)}")

            if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                glide.load(signalProfilePicture)
                    .placeholder(unknownRecipientDrawable)
                    .centerCrop()
                    .error(glide.load(placeholder))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(imageView)
            } else if (recipient.isCommunityRecipient && recipient.groupAvatarId == null) {
                glide.clear(imageView)
                glide.load(unknownOpenGroupDrawable)
                    .centerCrop()
                    .circleCrop()
                    .into(imageView)
            } else {
                glide.load(placeholder)
                    .placeholder(unknownRecipientDrawable)
                    .centerCrop()
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE).circleCrop().into(imageView)
            }
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
