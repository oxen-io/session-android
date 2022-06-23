package org.thoughtcrime.securesms.conversation.v2.messages

interface VisibleMessageViewDelegate {

    fun playVoiceMessageAtIndexIfPossible(indexInAdapter: Int)

    fun scrollToMessageIfPossible(timestamp: Long)

    fun onReactionClicked(messageId: Long, isMms: Boolean)

}