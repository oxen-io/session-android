package org.thoughtcrime.securesms.dms

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate

class CreatePrivateChatFragmentAdapter(
    val activity: FragmentActivity,
    private val enterPublicKeyDelegate: EnterPublicKeyDelegate,
    private val scanPublicKeyDelegate: ScanQRCodeWrapperFragmentDelegate
) : FragmentStateAdapter(activity) {

    val enterPublicKeyFragment: EnterPublicKeyFragment by lazy {
        EnterPublicKeyFragment()
    }

    var isKeyboardShowing = false
        set(value) {
            field = value
            enterPublicKeyFragment.isKeyboardShowing = isKeyboardShowing
        }

    override fun getItemCount(): Int  = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> {
                enterPublicKeyFragment.delegate = enterPublicKeyDelegate
                enterPublicKeyFragment
            }
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = scanPublicKeyDelegate
                result.message = activity.resources.getString(R.string.activity_create_private_chat_scan_qr_code_explanation)
                result
            }
            else -> throw IllegalStateException()
        }
    }

}