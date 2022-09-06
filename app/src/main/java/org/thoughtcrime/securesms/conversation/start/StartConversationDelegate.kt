package org.thoughtcrime.securesms.conversation.start

interface StartConversationDelegate {
    fun contactSelected(address: String)
    fun joinCommunity()
    fun createNewMessage()
    fun createNewGroup()
}