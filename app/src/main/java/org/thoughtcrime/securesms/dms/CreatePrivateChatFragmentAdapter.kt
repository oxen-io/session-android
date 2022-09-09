package org.thoughtcrime.securesms.dms

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate

class CreatePrivateChatFragmentAdapter(
    val activity: FragmentActivity,
    private val enterPublicKeyDelegate: EnterPublicKeyDelegate,
    private val scanPublicKeyDelegate: ScanQRCodeWrapperFragmentDelegate
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int  = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EnterPublicKeyFragment().apply { delegate = enterPublicKeyDelegate }
            1 ->  ScanQRCodeWrapperFragment().apply { delegate = scanPublicKeyDelegate }
            else -> throw IllegalStateException()
        }
    }

}