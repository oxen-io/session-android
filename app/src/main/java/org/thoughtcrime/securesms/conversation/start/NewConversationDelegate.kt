package org.thoughtcrime.securesms.conversation.start

interface StartConversationDelegate {
    fun onNewMessageSelected()
    fun onCreateGroupSelected()
    fun onJoinCommunitySelected()
    fun onContactSelected(address: String)
    fun onDialogBackPressed()
    fun onDialogClosePressed()
    fun onInviteFriend()
}

object NullStartConversationDelegate: StartConversationDelegate {
    override fun onNewMessageSelected() {}
    override fun onCreateGroupSelected() {}
    override fun onJoinCommunitySelected() {}
    override fun onContactSelected(address: String) {}
    override fun onDialogBackPressed() {}
    override fun onDialogClosePressed() {}
    override fun onInviteFriend() {}
}