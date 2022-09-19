package org.thoughtcrime.securesms.preferences

import android.os.Bundle
import androidx.activity.viewModels
import network.loki.messenger.databinding.ActivityBlockedContactsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity

class BlockedContactsActivity: PassphraseRequiredActionBarActivity() {

    lateinit var binding: ActivityBlockedContactsBinding

    val viewModel: BlockedContactsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityBlockedContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

}