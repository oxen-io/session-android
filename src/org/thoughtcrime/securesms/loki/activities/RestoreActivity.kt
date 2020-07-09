package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_restore.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.signalservice.loki.api.LokiSwarmAPI
import org.whispersystems.signalservice.loki.api.LokiSwarmAPI.Companion.configureIfNeeded
import org.whispersystems.signalservice.loki.api.fileserver.LokiFileServerAPI
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.hexEncodedPublicKey
import java.io.File
import java.io.FileOutputStream

class RestoreActivity : BaseActionBarActivity() {
    private lateinit var languageFileDirectory: File

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpLanguageFileDirectory()
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_restore)
        mnemonicEditText.imeOptions = mnemonicEditText.imeOptions or 16777216 // Always use incognito keyboard
        restoreButton.setOnClickListener { restore() }
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms of Service and Privacy Policy")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://getsession.org/terms-of-service/")
            }
        }, 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://getsession.org/privacy-policy/")
            }
        }, 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsTextView.movementMethod = LinkMovementMethod.getInstance()
        termsTextView.text = termsExplanation
    }
    // endregion

    // region General
    private fun setUpLanguageFileDirectory() {
        val languages = listOf( "english", "japanese", "portuguese", "spanish" )
        val directory = File(applicationInfo.dataDir)
        for (language in languages) {
            val fileName = "$language.txt"
            if (directory.list().contains(fileName)) { continue }
            val inputStream = assets.open("mnemonic/$fileName")
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(1024)
            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) { break }
                outputStream.write(buffer, 0, count)
            }
            inputStream.close()
            outputStream.close()
        }
        languageFileDirectory = directory
    }
    // endregion

    // region Interaction
    private fun restore() {
        val mnemonic = mnemonicEditText.text.toString()
        try {
            val hexEncodedSeed = MnemonicCodec(languageFileDirectory).decode(mnemonic)
            var seed = Hex.fromStringCondensed(hexEncodedSeed)
            IdentityKeyUtil.save(this, IdentityKeyUtil.lokiSeedKey, Hex.toStringCondensed(seed))
            if (seed.size == 16) { seed = seed + seed }
            val keyPair = Curve.generateKeyPair(seed)
            IdentityKeyUtil.save(this, IdentityKeyUtil.IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(keyPair.publicKey.serialize()))
            IdentityKeyUtil.save(this, IdentityKeyUtil.IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(keyPair.privateKey.serialize()))
            val userHexEncodedPublicKey = keyPair.hexEncodedPublicKey
            val registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(this, registrationID)
            DatabaseFactory.getIdentityDatabase(this).saveIdentity(Address.fromSerialized(userHexEncodedPublicKey),
                    IdentityKeyUtil.getIdentityKeyPair(this).publicKey, IdentityDatabase.VerifiedStatus.VERIFIED,
                    true, System.currentTimeMillis(), true)
            TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
            TextSecurePreferences.setRestorationTime(this, System.currentTimeMillis())
            TextSecurePreferences.setHasViewedSeed(this, true)
            // Loki: try to recover linked device after restoration
            val application = ApplicationContext.getInstance(this)
            val apiDB = DatabaseFactory.getLokiAPIDatabase(this)
            LokiSwarmAPI.Companion.configureIfNeeded(apiDB)
            application.setUpStorageAPIIfNeeded()
            LokiFileServerAPI.shared.getDeviceLinksForCurrentUser().success {
                for (deviceLink in it) {
                    val recipient = Recipient.from(this, Address.fromSerialized(deviceLink.slaveHexEncodedPublicKey), false)
                    MultiDeviceProtocol.sendSessionResetRequestToLinkedDevice(this, recipient)
                }
            }

            val intent = Intent(this, DisplayNameActivity::class.java)
            push(intent)
        } catch (e: Exception) {
            val message = if (e is MnemonicCodec.DecodingError) e.description else MnemonicCodec.DecodingError.Generic.description
            return Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openURL(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }
    // endregion
}