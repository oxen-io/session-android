package org.thoughtcrime.securesms.onboarding.landing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.onboarding.loadaccount.LoadAccountActivity
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.start
import javax.inject.Inject

@AndroidEntryPoint
class LandingActivity: BaseActionBarActivity() {

    @Inject
    internal lateinit var prefs: TextSecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We always hit this LandingActivity on launch - but if there is a previous instance of
        // Session then close this activity to resume the last activity from the previous instance.
        if (!isTaskRoot) { finish(); return }

        setUpActionBarSessionLogo(true)

        setComposeContent {
            LandingScreen(
                createAccount = {
                    prefs.setHasViewedSeed(false)
                    startPickDisplayNameActivity()
                },
                loadAccount = { start<LoadAccountActivity>() },
                openTerms = { open("https://getsession.org/terms-of-service") },
                openPrivacyPolicy = { open("https://getsession.org/privacy-policy") }
            )
        }

        IdentityKeyUtil.generateIdentityKeyPair(this)
        TextSecurePreferences.setPasswordDisabled(this, true)
        // AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    private fun open(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).let(::startActivity)
    }
}
