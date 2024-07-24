package org.thoughtcrime.securesms.conversation.start

import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.modifyLayoutParams
import org.thoughtcrime.securesms.conversation.start.home.StartConversationHomeFragment
import org.thoughtcrime.securesms.conversation.start.invitefriend.InviteFriendFragment
import org.thoughtcrime.securesms.conversation.start.newmessage.NewMessageFragment
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.groups.CreateGroupFragment
import org.thoughtcrime.securesms.groups.JoinCommunityFragment

@AndroidEntryPoint
class StartConversationFragment : BottomSheetDialogFragment(), StartConversationDelegate {

    private val defaultPeekHeight: Int by lazy { (Resources.getSystem().displayMetrics.heightPixels * 0.94).toInt() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_new_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        replaceFragment(
            fragment = StartConversationHomeFragment().also { it.delegate.value = this },
            fragmentKey = StartConversationHomeFragment::class.java.simpleName
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), R.style.Theme_Session_BottomSheet).apply {
            setOnShowListener { _ ->
                findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
                    modifyLayoutParams<LayoutParams> { height = defaultPeekHeight }
                }?.let { BottomSheetBehavior.from(it) }?.apply {
                    skipCollapsed = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }


    override fun onNewMessageSelected() {
        replaceFragment(NewMessageFragment().also { it.delegate = this })
    }

    override fun onCreateGroupSelected() {
        replaceFragment(CreateGroupFragment().also { it.delegate = this })
    }

    override fun onJoinCommunitySelected() {
        replaceFragment(JoinCommunityFragment().also { it.delegate = this })
    }

    override fun onContactSelected(address: String) {
        val intent = Intent(requireContext(), ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(address))
        requireContext().startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out)
    }

    override fun onDialogBackPressed() {
        childFragmentManager.popBackStack()
    }

    override fun onInviteFriend() {
        replaceFragment(InviteFriendFragment().also { it.delegate = this })
    }

    override fun onDialogClosePressed() {
        dismiss()
    }

    private fun replaceFragment(fragment: Fragment, fragmentKey: String? = null) {
        childFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_from_right,
                R.anim.fade_scale_out,
                0,
                R.anim.slide_to_right
            )
            replace(R.id.new_conversation_fragment_container, fragment)
            addToBackStack(fragmentKey)
        }
    }
}