package org.thoughtcrime.securesms.conversation.start

interface StartConversationDelegate {
    fun onNewMessageSelected()
    fun onCreateGroupSelected()
    fun onJoinCommunitySelected()
    fun onContactSelected(address: String)
    fun onDialogBackPressed()
}