package network.loki.messenger

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsignal.utilities.Base64

@RunWith(AndroidJUnit4::class)
class SodiumUtilitiesTest {

    private val serverPublicKey = "c3b3c6f32f0ab5a57f853cc4f30f5da7fda5624b0c77b3fb0829de562ada081d"
    private val pubKey = Key.fromHexString("bac6e71efd7dfa4a83c98ed24f254ab2c267f9ccdb172a5280a0444ad24e89cc")
    private val secKey = Key.fromHexString("c010d89eccbaf5d1c6d19df766c6eedf965d4a28a56f87c9fc819edb59896dd9")

    @Test
    fun blindedKeyPair() {
        val blindedKey = "98932d4bccbe595a8789d7eb1629cefc483a0eaddc7e20e8fe5c771efafd9af5"

        val keyPair = SodiumUtilities.blindedKeyPair(serverPublicKey, KeyPair(pubKey, secKey))!!

        assertThat(keyPair.publicKey.asHexString.lowercase(), equalTo(blindedKey))
    }

    @Test
    fun sogsSignature() {
        val expectedSignature = "K1N3A+H4dxV/wiN6Mr9cEj9TWUUqxESDoGW1cmoqDp7zMzCuCraTQKPX1tIiPuOBmFvB8VSUuYsHZrfGis1hDA=="
        val keyPair = SodiumUtilities.blindedKeyPair(serverPublicKey, KeyPair(pubKey, secKey))!!

        val message = serverPublicKey.toByteArray()
            .plus(Base64.decode("CaLsVaBSL9jarS5SwzJd8g=="))
            .plus("1647896537".toByteArray(Charsets.US_ASCII))
            .plus("GET".toByteArray())
            .plus("/room/sudoku/messages/recent?limit=25".toByteArray())
            .plus(ByteArray(0))
        val signature = Base64.encodeBytes(SodiumUtilities.sogsSignature(
            message,
            secKey.asBytes,
            keyPair.secretKey.asBytes,
            keyPair.publicKey.asBytes
        )!!)

        assertThat(signature, equalTo(expectedSignature))
    }

}