package org.thoughtcrime.securesms.groups

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate

class JoinCommunityFragmentAdapter(
    val activity: FragmentActivity,
    private val enterCommunityUrlDelegate: EnterCommunityUrlDelegate,
    private val scanQrCodeDelegate: ScanQRCodeWrapperFragmentDelegate
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EnterCommunityUrlFragment().apply { delegate = enterCommunityUrlDelegate }
            1 ->  ScanQRCodeWrapperFragment().apply { delegate = scanQrCodeDelegate }
            else -> throw IllegalStateException()
        }
    }
}