package org.thoughtcrime.securesms.conversation.start

interface StartConversationDelegate {
    fun contactSelected(address: String)
    fun joinCommunity()
    fun createPrivateChat()
    fun createClosedGroup()
}