package network.loki.messenger

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@SmallTest
class LibSessionTests {

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
        val contacts = Contacts.newInstance(key)
        contactList.forEach { contact ->
            contacts.set(contact)
        }
        return contacts.push().config
    }

    private fun buildVolatileMessage(conversations: List<Conversation>): ByteArray {
        val (key, _) = maybeGetUserInfo()!!
        val volatile = ConversationVolatileConfig.newInstance(key)
        conversations.forEach { conversation ->
            volatile.set(conversation)
        }
        return volatile.push().config
    }

    private fun fakePollNewConfig(configBase: ConfigBase, toMerge: ByteArray) {
        configBase.merge(nextFakeHash to toMerge)
        MessagingModuleConfiguration.shared.configFactory.persist(configBase, System.currentTimeMillis())
    }

    @Before
    fun setupUser() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext).edit {
            putBoolean(TextSecurePreferences.HAS_FORCED_NEW_CONFIG, true).apply()
        }
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
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        val storageSpy = spy(app.storage)
        app.storage = storageSpy

        val newContactId = randomSessionId()
        val singleContact = Contact(
            id = newContactId,
            approved = true,
            expiryMode = ExpiryMode.NONE
        )
        val newContactMerge = buildContactMessage(listOf(singleContact))
        val contacts = MessagingModuleConfiguration.shared.configFactory.contacts!!
        fakePollNewConfig(contacts, newContactMerge)
        verify(storageSpy).addLibSessionContacts(argThat {
            first().let { it.id == newContactId && it.approved } && size == 1
        }, any())
        verify(storageSpy).setRecipientApproved(argThat { address.serialize() == newContactId }, eq(true))
    }

    @Test
    fun test_expected_configs() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        val storageSpy = spy(app.storage)
        app.storage = storageSpy

        val randomRecipient = randomSessionId()
        val newContact = Contact(
            id = randomRecipient,
            approved = true,
            expiryMode = ExpiryMode.AfterSend(1000)
        )
        val newConvo = Conversation.OneToOne(
            randomRecipient,
            SnodeAPI.nowWithOffset,
            false
        )
        val volatiles = MessagingModuleConfiguration.shared.configFactory.convoVolatile!!
        val contacts = MessagingModuleConfiguration.shared.configFactory.contacts!!
        val newContactMerge = buildContactMessage(listOf(newContact))
        val newVolatileMerge = buildVolatileMessage(listOf(newConvo))
        fakePollNewConfig(contacts, newContactMerge)
        fakePollNewConfig(volatiles, newVolatileMerge)
        verify(storageSpy).setExpirationConfiguration(argWhere { config ->
            config.expiryMode is ExpiryMode.AfterSend
                    && config.expiryMode.expirySeconds == 1000L
        })
        val threadId = storageSpy.getThreadId(Address.fromSerialized(randomRecipient))!!
        val newExpiry = storageSpy.getExpirationConfiguration(threadId)!!
        assertThat(newExpiry.expiryMode, instanceOf(ExpiryMode.AfterSend::class.java))
        assertThat(newExpiry.expiryMode.expirySeconds, equalTo(1000))
        assertThat(newExpiry.expiryMode.expiryMillis, equalTo(1000000))
    }

    @Test
    fun test_overwrite_config() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        val storageSpy = spy(app.storage)
        app.storage = storageSpy

        // Initial state
        val randomRecipient = randomSessionId()
        val currentContact = Contact(
            id = randomRecipient,
            approved = true,
            expiryMode = ExpiryMode.NONE
        )
        val newConvo = Conversation.OneToOne(
            randomRecipient,
            SnodeAPI.nowWithOffset,
            false
        )
        val volatiles = MessagingModuleConfiguration.shared.configFactory.convoVolatile!!
        val contacts = MessagingModuleConfiguration.shared.configFactory.contacts!!
        val newContactMerge = buildContactMessage(listOf(currentContact))
        val newVolatileMerge = buildVolatileMessage(listOf(newConvo))
        fakePollNewConfig(contacts, newContactMerge)
        fakePollNewConfig(volatiles, newVolatileMerge)
        verify(storageSpy).setExpirationConfiguration(argWhere { config ->
            config.expiryMode == ExpiryMode.NONE
        })
        val threadId = storageSpy.getThreadId(Address.fromSerialized(randomRecipient))!!
        val currentExpiryConfig = storageSpy.getExpirationConfiguration(threadId)!!
        assertThat(currentExpiryConfig.expiryMode, equalTo(ExpiryMode.NONE))
        assertThat(currentExpiryConfig.expiryMode.expirySeconds, equalTo(0))
        assertThat(currentExpiryConfig.expiryMode.expiryMillis, equalTo(0))
        // Set new state and overwrite
        val updatedContact = currentContact.copy(expiryMode = ExpiryMode.AfterSend(1000))
        val updateContactMerge = buildContactMessage(listOf(updatedContact))
        fakePollNewConfig(contacts, updateContactMerge)
        val updatedExpiryConfig = storageSpy.getExpirationConfiguration(threadId)!!
        assertThat(updatedExpiryConfig.expiryMode, instanceOf(ExpiryMode.AfterSend::class.java))
        assertThat(updatedExpiryConfig.expiryMode.expirySeconds, equalTo(1000))
    }

}