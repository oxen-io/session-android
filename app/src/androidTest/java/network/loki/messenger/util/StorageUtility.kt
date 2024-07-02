package network.loki.messenger.util

import androidx.test.platform.app.InstrumentationRegistry
import org.mockito.kotlin.spy
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.database.Storage
import kotlin.random.Random

fun maybeGetUserInfo(): Pair<ByteArray, String>? {
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

fun ApplicationContext.applySpiedStorage(): Storage {
    val storageSpy = spy(storage)!!
    storage = storageSpy
    return storageSpy
}

fun randomSeedBytes() = (0 until 16).map { Random.nextInt(UByte.MAX_VALUE.toInt()).toByte() }
fun randomKeyPair() = KeyPairUtilities.generate(randomSeedBytes().toByteArray())
fun randomSessionId() = randomKeyPair().x25519KeyPair.hexEncodedPublicKey
