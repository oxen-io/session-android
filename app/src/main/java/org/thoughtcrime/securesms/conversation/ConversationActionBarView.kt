package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DimenRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewConversationActionBarBinding
import network.loki.messenger.databinding.ViewConversationSettingBinding
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionManagerUtilities
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ConversationActionBarView : LinearLayout {

    private lateinit var binding: ViewConversationActionBarBinding

    @Inject lateinit var lokiApiDb: LokiAPIDatabase
    @Inject lateinit var groupDb: GroupDatabase

    var delegate: ConversationActionBarDelegate? = null

    private val settingsAdapter = ConversationSettingsAdapter { setting ->
        if (setting.settingType == ConversationSettingType.EXPIRATION) {
            delegate?.onExpirationSettingClicked()
        }
    }

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewConversationActionBarBinding.inflate(LayoutInflater.from(context), this, true)
        binding.settingsPager.adapter = settingsAdapter
        val mediator = TabLayoutMediator(binding.settingsTabLayout, binding.settingsPager) { _, _ -> }
        mediator.attach()
    }

    fun bind(threadId: Long, recipient: Recipient, glide: GlideRequests) {
        binding.conversationTitleView.text = when {
            recipient.isLocalNumber -> context.getString(R.string.note_to_self)
            else -> recipient.toShortString()
        }
        @DimenRes val sizeID: Int = if (recipient.isClosedGroupRecipient) {
            R.dimen.medium_profile_picture_size
        } else {
            R.dimen.small_profile_picture_size
        }
        val size = resources.getDimension(sizeID).roundToInt()
        binding.profilePictureView.root.layoutParams = LayoutParams(size, size)
        binding.profilePictureView.root.glide = glide
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(threadId, context)
        binding.profilePictureView.root.update(recipient)
    }

    fun update(recipient: Recipient) {
        binding.profilePictureView.root.update(recipient)
        binding.conversationTitleView.text = when {
            recipient.isLocalNumber -> context.getString(R.string.note_to_self)
            else -> recipient.toShortString()
        }
        updateSubtitle(recipient)
    }

    fun updateSubtitle(recipient: Recipient, openGroup: OpenGroup? = null) {
        val settings = mutableListOf<ConversationSetting>()
        if (recipient.expireMessages > 0) {
            settings.add(
                ConversationSetting(
                    "${context.getString(R.string.expiration_type_disappear_after_read)} - ${ExpirationUtil.getExpirationAbbreviatedDisplayValue(context, recipient.expireMessages)}" ,
                    ConversationSettingType.EXPIRATION,
                    R.drawable.ic_timer
                )
            )
        }
        if (recipient.isMuted) {
            settings.add(
                ConversationSetting(
                    if (recipient.mutedUntil != Long.MAX_VALUE) {
                        context.getString(R.string.ConversationActivity_muted_until_date, DateUtils.getFormattedDateTime(recipient.mutedUntil, "EEE, MMM d, yyyy HH:mm", Locale.getDefault()))
                    } else {
                        context.getString(R.string.ConversationActivity_muted_forever)
                    },
                    ConversationSettingType.NOTIFICATION,
                    R.drawable.ic_outline_notifications_off_24
                )
            )
        }
        if (recipient.isGroupRecipient) {
            settings.add(
                ConversationSetting(
                openGroup?.let {
                    val userCount = lokiApiDb.getUserCount(it.room, it.server) ?: 0
                    context.getString(R.string.ConversationActivity_active_member_count, userCount)
                } ?: run {
                        val userCount = groupDb.getGroupMemberAddresses(recipient.address.toGroupString(), true).size
                        context.getString(R.string.ConversationActivity_member_count, userCount)
                    },
                    ConversationSettingType.MEMBER_COUNT,
                )
            )
        }
        settingsAdapter.submitList(settings)
        binding.settingsTabLayout.isVisible = settings.size > 1
    }

    class ConversationSettingsAdapter(
        private val settingsListener: (ConversationSetting) -> Unit
    ) : ListAdapter<ConversationSetting, ConversationSettingsAdapter.SettingViewHolder>(SettingsDiffer()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            return SettingViewHolder(ViewConversationSettingBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
            holder.bind(getItem(position), itemCount) {
                settingsListener.invoke(it)
            }
        }

        class SettingViewHolder(
            private val binding: ViewConversationSettingBinding
        ): RecyclerView.ViewHolder(binding.root) {

            fun bind(setting: ConversationSetting, itemCount: Int, listener: (ConversationSetting) -> Unit) {
                binding.root.setOnClickListener { listener.invoke(setting) }
                binding.iconImageView.setImageResource(setting.iconResId)
                binding.iconImageView.isVisible = setting.iconResId > 0
                binding.titleView.text = setting.title
                binding.leftArrowImageView.isVisible = itemCount > 1
                binding.rightArrowImageView.isVisible = itemCount > 1
            }

        }

        class SettingsDiffer: DiffUtil.ItemCallback<ConversationSetting>() {
            override fun areItemsTheSame(oldItem: ConversationSetting, newItem: ConversationSetting) = oldItem === newItem
            override fun areContentsTheSame(oldItem: ConversationSetting, newItem: ConversationSetting) = oldItem == newItem
        }

    }

}


fun interface ConversationActionBarDelegate {
    fun onExpirationSettingClicked()
}

data class ConversationSetting(
    val title: String,
    val settingType: ConversationSettingType,
    val iconResId: Int = 0
)

enum class ConversationSettingType {
    EXPIRATION,
    MEMBER_COUNT,
    NOTIFICATION
}