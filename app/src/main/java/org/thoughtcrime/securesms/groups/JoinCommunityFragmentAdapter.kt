package org.thoughtcrime.securesms.groups

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import network.loki.messenger.R
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
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = scanQrCodeDelegate
                result.message =
                    activity.resources.getString(R.string.activity_join_public_chat_scan_qr_code_explanation)
                result
            }
            else -> throw IllegalStateException()
        }
    }
}