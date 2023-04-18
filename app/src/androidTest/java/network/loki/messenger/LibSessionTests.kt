package network.loki.messenger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import kotlin.random.Random

@CustomTestApplication(ApplicationContext::class)
interface HiltApplicationContext

@RunWith(AndroidJUnit4::class)
@SmallTest
@HiltAndroidTest
class LibSessionTests {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private fun randomSeedBytes() = (0 until 16).map { Random.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
    private fun randomKeyPair() = KeyPairUtilities.generate(randomSeedBytes().toByteArray())
    private fun randomSessionId() = randomKeyPair().x25519KeyPair.hexEncodedPublicKey

    private var fakeHashI = 0
    private val nextFakeHash: String
        get() = "fakehash${fakeHashI++}"

    private fun maybeGetUserInfo(): Pair<ByteArray, String>? {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        val prefs = appContext.prefs
        val localUserPublicKey = prefs.getLocalNumber()
        val secretKey = with(appContext) {
            val edKey = KeyPairUtilities.getUserED25519KeyPair(this) ?: return null
            edKey.secretKey.asBytes
        }
        return if (localUserPublicKey == null || secretKey == null) null
        else secretKey to localUserPublicKey
    }

    private fun buildContactMessage(contactList: List<Contact>): ByteArray {
        val (key,_) = maybeGetUserInfo()!!
        val contacts = Contacts.Companion.newInstance(key)
        contactList.forEach { contact ->
            contacts.set(contact)
        }
        return contacts.push().config
    }

    private fun fakePollNewConfig(configBase: ConfigBase, toMerge: ByteArray) {
        configBase.merge(nextFakeHash to toMerge)
        MessagingModuleConfiguration.shared.configFactory.persist(configBase)
    }

    @Before
    fun setupUser() {
        val newBytes = randomSeedBytes().toByteArray()
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val kp = KeyPairUtilities.generate(newBytes)
        KeyPairUtilities.store(context, kp.seed, kp.ed25519KeyPair, kp.x25519KeyPair)
        val registrationID = KeyHelper.generateRegistrationId(false)
        TextSecurePreferences.setLocalRegistrationId(context, registrationID)
        TextSecurePreferences.setLocalNumber(context, kp.x25519KeyPair.hexEncodedPublicKey)
        TextSecurePreferences.setRestorationTime(context, 0)
        TextSecurePreferences.setHasViewedSeed(context, false)
    }

    @Test
    fun migration_one_to_ones() {
        val storageSpy = spy(MessagingModuleConfiguration.shared.storage)
        whenever(MessagingModuleConfiguration.shared.storage).thenReturn(storageSpy)

        val newContactId = randomSessionId()
        val singleContact = Contact(
            id = newContactId,
            approved = true,
            expiryMode = ExpiryMode.NONE
        )
        val newContactMerge = buildContactMessage(listOf(singleContact))
        val contacts = MessagingModuleConfiguration.shared.configFactory.contacts!!
        fakePollNewConfig(contacts, newContactMerge)
        verify(storageSpy).addLibSessionContacts(any())
    }

}