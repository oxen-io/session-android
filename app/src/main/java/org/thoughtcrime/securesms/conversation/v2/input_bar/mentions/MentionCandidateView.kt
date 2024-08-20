package org.thoughtcrime.securesms.conversation.v2.input_bar.mentions

import android.view.View
import network.loki.messenger.databinding.ViewMentionCandidateV2Binding
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.conversation.v2.mention.MentionViewModel

fun ViewMentionCandidateV2Binding.update(candidate: MentionViewModel.Candidate) {
    mentionCandidateNameTextView.text = candidate.nameHighlighted
    profilePictureView.load(Address.fromSerialized(candidate.member.publicKey))
    moderatorIconImageView.visibility = if (candidate.member.isModerator) View.VISIBLE else View.GONE
}
