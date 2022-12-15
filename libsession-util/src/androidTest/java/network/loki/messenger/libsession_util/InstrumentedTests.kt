package network.loki.messenger.libsession_util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.KeyPair
import network.loki.messenger.libsession_util.util.Sodium
import network.loki.messenger.libsession_util.util.UserPic
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsignal.utilities.Hex

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    val seed =
        Hex.fromStringCondensed("0123456789abcdef0123456789abcdef00000000000000000000000000000000")

    private val keyPair: KeyPair
        get() {
            return Sodium.ed25519KeyPair(seed)
        }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("network.loki.messenger.libsession_util.test", appContext.packageName)
    }

    @Test
    fun jni_test_sodium_kp_ed_curve() {
        val kp = keyPair
        val curvePkBytes = Sodium.ed25519PkToCurve25519(kp.pubKey)

        val edPk = kp.pubKey
        val curvePk = curvePkBytes

        assertArrayEquals(Hex.fromStringCondensed("4cb76fdc6d32278e3f83dbf608360ecc6b65727934b85d2fb86862ff98c46ab7"), edPk)
        assertArrayEquals(Hex.fromStringCondensed("d2ad010eeb72d72e561d9de7bd7b6989af77dcabffa03a5111a6c859ae5c3a72"), curvePk)
        assertArrayEquals(kp.secretKey.take(32).toByteArray(), seed)
    }

    @Test
    fun jni_contacts() {
        val contacts = Contacts.newInstance(keyPair.secretKey)
        val definitelyRealId = "050000000000000000000000000000000000000000000000000000000000000000"
        assertNull(contacts.get(definitelyRealId))

        // Should be an uninitialized contact apart from ID
        val c = contacts.getOrCreate(definitelyRealId)
        assertEquals(definitelyRealId, c.id)
        assertNull(c.name)
        assertNull(c.nickname)
        assertFalse(c.approved)
        assertFalse(c.approvedMe)
        assertFalse(c.blocked)
        assertNull(c.profilePicture)

        assertFalse(contacts.needsPush())
        assertFalse(contacts.needsDump())
        assertEquals(0, contacts.push().seqNo)

        c.name = "Joe"
        c.nickname = "Joey"
        c.approved = true
        c.approvedMe = true

        contacts.set(c)

        val cSaved = contacts.get(definitelyRealId)!!
        assertEquals("Joe", cSaved.name)
        assertEquals("Joey", cSaved.nickname)
        assertTrue(cSaved.approved)
        assertTrue(cSaved.approvedMe)
        assertFalse(cSaved.blocked)
        assertNull(cSaved.profilePicture)

        val push1 = contacts.push()

        assertEquals(1, push1.seqNo)
        contacts.confirmPushed(push1.seqNo)
        assertFalse(contacts.needsPush())
        assertTrue(contacts.needsDump())

        val contacts2 = Contacts.newInstance(keyPair.secretKey, contacts.dump())
        assertFalse(contacts.needsDump())
        assertFalse(contacts2.needsPush())
        assertFalse(contacts2.needsDump())

        val anotherId = "051111111111111111111111111111111111111111111111111111111111111111"
        val c2 = contacts2.getOrCreate(anotherId)
        contacts2.set(c2)
        val push2 = contacts2.push()
        assertEquals(2, push2.seqNo)
        contacts2.confirmPushed(push2.seqNo)
        assertFalse(contacts2.needsPush())

        contacts.merge(push2.config)


        assertFalse(contacts.needsPush())
        assertEquals(push2.seqNo, contacts.push().seqNo)

        val contactList = contacts.all().toList()
        assertEquals(definitelyRealId, contactList[0].id)
        assertEquals(anotherId, contactList[1].id)
        assertEquals("Joey", contactList[0].nickname)
        assertNull(contactList[1].nickname)

        contacts.erase(definitelyRealId)

        val thirdId ="052222222222222222222222222222222222222222222222222222222222222222"
        val third = Contact(
            id = thirdId,
            nickname = "Nickname 3",
            approved = true,
            blocked = true,
            profilePicture = UserPic("http://example.com/huge.bmp", "qwerty".encodeToByteArray())
        )
        contacts2.set(third)
        assertTrue(contacts.needsPush())
        assertTrue(contacts2.needsPush())
        val toPush = contacts.push()
        val toPush2 = contacts2.push()
        assertEquals(toPush.seqNo, toPush2.seqNo)
        assertThat(toPush2.config, not(equals(toPush.config)))

        contacts.confirmPushed(toPush.seqNo)
        contacts2.confirmPushed(toPush2.seqNo)

        contacts.merge(toPush2.config)
        contacts2.merge(toPush.config)

        assertTrue(contacts.needsPush())
        assertTrue(contacts2.needsPush())

        val mergePush = contacts.push()
        val mergePush2 = contacts2.push()

        assertEquals(mergePush.seqNo, mergePush2.seqNo)
        assertArrayEquals(mergePush.config, mergePush2.config)

    }

    @Test
    fun jni_accessible() {
        val userProfile = UserProfile.newInstance(keyPair.secretKey)
        assertNotNull(userProfile)
        userProfile.free()
    }

    @Test
    fun jni_user_profile_c_api() {
        val edSk = keyPair.secretKey
        val userProfile = UserProfile.newInstance(edSk)

        // these should be false as empty config
        assertFalse(userProfile.needsPush())
        assertFalse(userProfile.needsDump())

        // Since it's empty there shouldn't be a name
        assertNull(userProfile.getName())

        // Don't need to push yet so this is just for testing
        val (_, seqNo) = userProfile.push() // disregarding encrypted
        assertEquals("UserProfile", userProfile.encryptionDomain())
        assertEquals(0, seqNo)

        // This should also be unset:
        assertNull(userProfile.getPic())

        // Now let's go set a profile name and picture:
        // not sending keylen like c api so cutting off the NOTSECRET in key for testing purposes
        userProfile.setName("Kallie")
        val newUserPic = UserPic("http://example.org/omg-pic-123.bmp", "secret".encodeToByteArray())
        userProfile.setPic(newUserPic)

        // Retrieve them just to make sure they set properly:
        assertEquals("Kallie", userProfile.getName())
        val pic = userProfile.getPic()
        assertEquals("http://example.org/omg-pic-123.bmp", pic?.url)
        assertEquals("secret", pic?.key?.decodeToString())

        // Since we've made changes, we should need to push new config to the swarm, *and* should need
        // to dump the updated state:
        assertTrue(userProfile.needsPush())
        assertTrue(userProfile.needsDump())
        val (newToPush, newSeqNo) = userProfile.push()

        val expHash0 =
            Hex.fromStringCondensed("ea173b57beca8af18c3519a7bbf69c3e7a05d1c049fa9558341d8ebb48b0c965")

        val expectedPush1Decrypted = ("d" +
                "1:#" + "i1e"+
                "1:&" + "d"+
                "1:n" + "6:Kallie"+
                "1:p" + "34:http://example.org/omg-pic-123.bmp"+
                "1:q" + "6:secret"+
                "e"+
                "1:<" + "l"+
                "l" + "i0e" + "32:").encodeToByteArray() + expHash0 + ("de" + "e" +
                "e" +
                "1:=" + "d" +
                "1:n" + "0:" +
                "1:p" + "0:" +
                "1:q" + "0:" +
                "e" +
                "e").encodeToByteArray()

        val expectedPush1Encrypted = Hex.fromStringCondensed(
            "a2952190dcb9797bc48e48f6dc7b3254d004bde9091cfc9ec3433cbc5939a3726deb04f58a546d7d79e6f8" +
                    "0ea185d43bf93278398556304998ae882304075c77f15c67f9914c4d10005a661f29ff7a79e0a9de7f2172" +
                    "5ba3b5a6c19eaa3797671b8fa4008d62e9af2744629cbb46664c4d8048e2867f66ed9254120371bdb24e95" +
                    "b2d92341fa3b1f695046113a768ceb7522269f937ead5591bfa8a5eeee3010474002f2db9de043f0f0d1cf" +
                    "b1066a03e7b5d6cfb70a8f84a20cd2df5a510cd3d175708015a52dd4a105886d916db0005dbea5706e5a5d" +
                    "c37ffd0a0ca2824b524da2e2ad181a48bb38e21ed9abe136014a4ee1e472cb2f53102db2a46afa9d68"
        )

        assertEquals(1, newSeqNo)
        assertArrayEquals(expectedPush1Encrypted, newToPush)
        // We haven't dumped, so still need to dump:
        assertTrue(userProfile.needsDump())
        // We did call push but we haven't confirmed it as stored yet, so this will still return true:
        assertTrue(userProfile.needsPush())

        val dump = userProfile.dump()
        // (in a real client we'd now store this to disk)
        assertFalse(userProfile.needsDump())
        val expectedDump = ("d" +
                "1:!i2e" +
                "1:$").encodeToByteArray() + expectedPush1Decrypted.size.toString().encodeToByteArray() +
                ":".encodeToByteArray() + expectedPush1Decrypted +
                "e".encodeToByteArray()

        assertArrayEquals(expectedDump, dump)

        val newConf = UserProfile.newInstance(edSk)

        val accepted = newConf.merge(arrayOf(expectedPush1Encrypted))
        assertEquals(1, accepted)

        assertTrue(newConf.needsDump())
        assertFalse(newConf.needsPush())
        val _ignore = newConf.dump()
        assertFalse(newConf.needsDump())


        userProfile.setName("Raz")
        newConf.setName("Nibbler")
        newConf.setPic(UserPic("http://new.example.com/pic", "qwertyuio".encodeToByteArray()))

        val conf = userProfile.push()
        val conf2 = newConf.push()

        userProfile.dump()
        userProfile.dump()

        assertFalse(conf.config.contentEquals(conf2.config))

        newConf.merge(arrayOf(conf.config))
        userProfile.merge(arrayOf(conf2.config))

        assertTrue(newConf.needsPush())
        assertTrue(userProfile.needsPush())

        val newSeq1 = userProfile.push()

        assertEquals(3, newSeq1.seqNo)

        // assume newConf push gets rejected as it was last to write and clear previous config by hash on oxenss
        newConf.merge(arrayOf(newSeq1.config))

        val newSeqMerge = newConf.push()

        assertEquals("Nibbler", newConf.getName())
        assertEquals(3, newSeqMerge.seqNo)

        // userProfile device polls and merges
        userProfile.merge(arrayOf(newSeqMerge.config))


        val userConfigMerge = userProfile.push()

        assertEquals(3, userConfigMerge.seqNo)

        assertEquals("Nibbler", newConf.getName())
        assertEquals("Nibbler", userProfile.getName())

        userProfile.free()
        newConf.free()
    }

    @Test
    fun merge_resolves_conflicts() {
        val kp = keyPair
        val a = UserProfile.newInstance(kp.secretKey)
        val b = UserProfile.newInstance(kp.secretKey)
        a.setName("A")
        val (aPush, aSeq) = a.push()
        b.setName("B")
        // polls and sees invalid state, has to merge
        b.merge(aPush)
        val (bPush, bSeq) = b.push()
        assertEquals("B", b.getName())
        assertEquals(1, aSeq)
        assertEquals(2, bSeq)
        a.merge(bPush)
        assertEquals(2, a.push().seqNo)
    }

    @Test
    fun jni_setting_getting() {
        val userProfile = UserProfile.newInstance(keyPair.secretKey)
        val newName = "test"
        println("Name being set via JNI call: $newName")
        userProfile.setName(newName)
        val nameFromNative = userProfile.getName()
        assertEquals(newName, nameFromNative)
        println("Name received by JNI call: $nameFromNative")
        assertTrue(userProfile.dirty())
        userProfile.free()
    }

}