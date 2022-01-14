package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewOpenGroupInvitationBinding
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.OpenGroupUrlParser
import org.thoughtcrime.securesms.conversation.v2.dialogs.JoinOpenGroupDialog
import org.thoughtcrime.securesms.database.model.MessageRecord

class OpenGroupInvitationView : LinearLayout {
    private lateinit var binding: ViewOpenGroupInvitationBinding
    private var data: UpdateMessageData.Kind.OpenGroupInvitation? = null

    constructor(context: Context): super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewOpenGroupInvitationBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun bind(message: MessageRecord, @ColorInt textColor: Int) {
        // FIXME: This is a really weird approach...
        val umd = UpdateMessageData.fromJSON(message.body)!!
        val data = umd.kind as UpdateMessageData.Kind.OpenGroupInvitation
        this.data = data
        val iconID = if (message.isOutgoing) R.drawable.ic_globe else R.drawable.ic_plus
        with(binding){
            openGroupInvitationIconImageView.setImageResource(iconID)
            openGroupTitleTextView.text = data.groupName
            openGroupURLTextView.text = OpenGroupUrlParser.trimQueryParameter(data.groupUrl)
            openGroupTitleTextView.setTextColor(textColor)
            openGroupJoinMessageTextView.setTextColor(textColor)
            openGroupURLTextView.setTextColor(textColor)
        }
    }

    fun joinOpenGroup() {
        val data = data ?: return
        val activity = context as AppCompatActivity
        JoinOpenGroupDialog(data.groupName, data.groupUrl).show(activity.supportFragmentManager, "Join Open Group Dialog")
    }
}