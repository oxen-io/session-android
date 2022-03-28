package org.session.libsession.messaging.utilities

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.Hash
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.toHexString
import kotlin.experimental.inv

object SodiumUtilities {

    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    /* 64-byte blake2b hash then reduce to get the blinding factor */
    private fun generateBlindingFactor(serverPublicKey: String): ByteArray? {
        // k = salt.crypto_core_ed25519_scalar_reduce(blake2b(server_pk, digest_size=64).digest())
        val serverPubKeyData = Hex.fromStringCondensed(serverPublicKey)
        val serverPubKeyHash = ByteArray(GenericHash.BYTES_MAX)
        if (!sodium.cryptoGenericHash(serverPubKeyHash, serverPubKeyHash.size, serverPubKeyData, serverPubKeyData.size.toLong())) {
            return null
        }
        // Reduce the server public key into an ed25519 scalar (`k`)
        val x25519PublicKey = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        sodium.cryptoCoreEd25519ScalarReduce(x25519PublicKey, serverPubKeyHash)
        return if (x25519PublicKey.any { it.toInt() != 0 }) {
            x25519PublicKey
        } else  null
    }

    /*
     Calculate k*a. To get 'a' (the Ed25519 private key scalar) we call the sodium function to
     convert to an *x* secret key, which seems wrong--but isn't because converted keys use the
     same secret scalar secret (and so this is just the most convenient way to get 'a' out of
     a sodium Ed25519 secret key)
    */
    private fun generatePrivateKeyScalar(secretKey: ByteArray): ByteArray? {
        // a = s.to_curve25519_private_key().encode()
        val aBytes = ByteArray(Sign.PUBLICKEYBYTES)
        return if (sodium.convertSecretKeyEd25519ToCurve25519(aBytes, secretKey)) {
            aBytes
        } else null
    }

    /* Constructs a "blinded" key pair (`ka, kA`) based on an open group server `publicKey` and an ed25519 `keyPair` */
    fun blindedKeyPair(serverPublicKey: String, edKeyPair: KeyPair): KeyPair?  {
        if (edKeyPair.publicKey.asBytes.size != Sign.PUBLICKEYBYTES ||
            edKeyPair.secretKey.asBytes.size != Sign.SECRETKEYBYTES) return null
        val kBytes = generateBlindingFactor(serverPublicKey)
        val aBytes = generatePrivateKeyScalar(edKeyPair.secretKey.asBytes)
        // Generate the blinded key pair `ka`, `kA`
        val kaBytes = ByteArray(Sign.SECRETKEYBYTES)
        sodium.cryptoCoreEd25519ScalarMul(kaBytes, kBytes, aBytes)
        if (kaBytes.all { it.toInt() == 0 }) {
            return null
        }
        val kABytes = ByteArray(Sign.PUBLICKEYBYTES)
        sodium.cryptoScalarMultE25519BaseNoClamp(kABytes, kaBytes)
        return if (kABytes.any { it.toInt() != 0 }) {
            KeyPair(Key.fromBytes(kABytes), Key.fromBytes(kaBytes))
        } else {
            null
        }
    }

    /*
     Constructs an Ed25519 signature from a root Ed25519 key and a blinded scalar/pubkey pair, with one tweak to the
     construction: we add kA into the hashed value that yields r so that we have domain separation for different blinded
     pubkeys (this doesn't affect verification at all)
    */
    fun sogsSignature(
        message: ByteArray,
        secretKey: ByteArray,
        ka: ByteArray, /*blindedSecretKey*/
        kA: ByteArray /*blindedPublicKey*/
    ): ByteArray? {
        // H_rh = sha512(s.encode()).digest()[32:]
        val h_rh = ByteArray(Hash.SHA512_BYTES)
        if (!sodium.cryptoHashSha512(h_rh, secretKey, secretKey.size.toLong())) return null

        // r = salt.crypto_core_ed25519_scalar_reduce(sha512_multipart(H_rh, kA, message_parts))
        val combinedData = h_rh + kA + message
        val combinedHash = ByteArray(Hash.SHA512_BYTES)
        if (!sodium.cryptoHashSha512(combinedHash, combinedData, combinedData.size.toLong())) return null
        val rHash = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        sodium.cryptoCoreEd25519ScalarReduce(rHash, combinedHash)
        if (rHash.all { it.toInt() == 0 }) {
            return null
        }

        // sig_R = salt.crypto_scalarmult_ed25519_base_noclamp(r)
        val sig_R = ByteArray(Sign.SECRETKEYBYTES)
        if (!sodium.cryptoScalarMultBase(sig_R, rHash)) return null

        // HRAM = salt.crypto_core_ed25519_scalar_reduce(sha512_multipart(sig_R, kA, message_parts))
        val hRamData = sig_R + kA + message
        val hRamHash = ByteArray(Hash.SHA512_BYTES)
        if (!sodium.cryptoHashSha512(hRamHash, hRamData, hRamData.size.toLong())) return null
        val hRam = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        sodium.cryptoCoreEd25519ScalarReduce(hRam, hRamHash)
        if (hRam.all { it.toInt() == 0 }) {
            return null
        }
        // sig_s = salt.crypto_core_ed25519_scalar_add(r, salt.crypto_core_ed25519_scalar_mul(HRAM, ka))
        val sig_sMul = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        val sig_s = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        if (sodium.cryptoScalarMult(sig_sMul, hRam, ka)) {
            sodium.cryptoCoreEd25519ScalarReduce(sig_s/*,  rHash*/, sig_sMul)
        } else return null

        return sig_R + sig_s
    }

    /* Combines two keys (`kA`) */
    private fun combineKeys(lhsKey: ByteArray, rhsKey: ByteArray): ByteArray? {
        return sodium.cryptoScalarMult(Key.fromBytes(lhsKey), Key.fromBytes(rhsKey)).asBytes
    }

    /*
     Calculate a shared secret for a message from A to B:
     BLAKE2b(a kB || kA || kB)
     The receiver can calculate the same value via:
     BLAKE2b(b kA || kA || kB)
    */
    fun sharedBlindedEncryptionKey(
        secretKey: ByteArray,
        otherBlindedPublicKey: ByteArray,
        kA: ByteArray, /*fromBlindedPublicKey*/
        kB: ByteArray  /*toBlindedPublicKey*/
    ): ByteArray? {
        val aBytes = generatePrivateKeyScalar(secretKey) ?: return null
        val combinedKeyBytes = combineKeys(aBytes, otherBlindedPublicKey) ?: return null
        val outputHash = ByteArray(GenericHash.KEYBYTES)
        val inputBytes = combinedKeyBytes + kA + kB
        return if (sodium.cryptoGenericHash(outputHash, outputHash.size, inputBytes, inputBytes.size.toLong())) {
            outputHash
        } else null
    }

    /* This method should be used to check if a users standard sessionId matches a blinded one */
    fun sessionId(
        standardSessionId: String,
        blindedSessionId: String,
        serverPublicKey: String
    ): Boolean {
        // Only support generating blinded keys for standard session ids
        val sessionId = SessionId(standardSessionId)
        if (sessionId.prefix != IdPrefix.STANDARD) return false
        val blindedId = SessionId(blindedSessionId)
        if (blindedId.prefix != IdPrefix.BLINDED) return false
        val k = generateBlindingFactor(serverPublicKey) ?: return false

        // From the session id (ignoring 05 prefix) we have two possible ed25519 pubkeys; the first is the positive (which is what
        // Signal's XEd25519 conversion always uses)
        val xEd25519Key = Key.fromHexString(sessionId.publicKey).asBytes

        // Blind the positive public key
        val pk1 = combineKeys(k, xEd25519Key) ?: return false

        // For the negative, what we're going to get out of the above is simply the negative of pk1, so flip the sign bit to get pk2
        //     pk2 = pk1[0:31] + bytes([pk1[31] ^ 0b1000_0000])
        val pk2 = pk1.take(31).toByteArray() + listOf(pk1.last().inv()).toByteArray()

        return SessionId(IdPrefix.BLINDED, pk1).publicKey == blindedId.publicKey ||
                SessionId(IdPrefix.BLINDED, pk2).publicKey == blindedId.publicKey
    }

    class SessionId {
        var prefix: IdPrefix?
        var publicKey: String

        constructor(id: String) {
            prefix = IdPrefix.fromValue(id.take(2))
            publicKey = id.drop(2)
        }

        constructor(prefix: IdPrefix, publicKey: ByteArray) {
            this.prefix = prefix
            this.publicKey = publicKey.toHexString()
        }

        val hexString
            get() = prefix?.value + publicKey
    }

    enum class IdPrefix(val value: String) {
        STANDARD("05"), BLINDED("15"), UN_BLINDED("00");

        companion object {
            fun fromValue(rawValue: String): IdPrefix? = when(rawValue) {
                STANDARD.value -> STANDARD
                BLINDED.value -> BLINDED
                UN_BLINDED.value -> UN_BLINDED
                else -> null
            }
        }

    }
}
