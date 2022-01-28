package org.thoughtcrime.securesms.messagerequests

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.databinding.ActivityMessageRequestsBinding
import org.thoughtcrime.securesms.BaseActionBarActivity

@AndroidEntryPoint
class MessageRequestsActivity : BaseActionBarActivity() {

    private lateinit var binding: ActivityMessageRequestsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

}