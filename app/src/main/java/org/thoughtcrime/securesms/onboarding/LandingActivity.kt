package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.os.Bundle
import network.loki.messenger.databinding.ActivityLandingBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("[ACL]", "Hit LandingActivity.onCreate")

        super.onCreate(savedInstanceState)

        // If we have an existing instance of Session running then close this activity so that we
        // resume the previous activity
        if (!isTaskRoot) {
            Log.d("[ACL]", "LandingActivity thinks it IS NOT Task root - finishing this activity!")
            finish()
            return
        } else { Log.d("[ACL]", "LandingActivity thinks it IS the Task root!") }

        val binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpActionBarSessionLogo(true)
        with(binding) {
            fakeChatView.startAnimating()
            registerButton.setOnClickListener { register() }
            restoreButton.setOnClickListener { link() }
            linkButton.setOnClickListener { link() }
        }
        IdentityKeyUtil.generateIdentityKeyPair(this)
        TextSecurePreferences.setPasswordDisabled(this, true)
        // AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    override fun onResume() {
        Log.d("[ACL]", "Hit LandingActivity.onResume!")
        super.onResume()
    }

    override fun onStart() {
        Log.d("[ACL]", "Hit LandingActivity.onStart!")
        super.onStart()
    }

    override fun onPause() {
        Log.d("[ACL]", "Hit LandingActivity.onPause!")
        super.onPause()
    }

    override fun onStop() {
        Log.d("[ACL]", "Hit LandingActivity.onStop!")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d("[ACL]", "Hit LandingActivity.onDestroy!")
        super.onDestroy()
    }

    private fun register() {
        val intent = Intent(this, RegisterActivity::class.java)
        push(intent)
    }

    private fun link() {
        val intent = Intent(this, LinkDeviceActivity::class.java)
        push(intent)
    }
}