package network.loki.messenger.libsession_util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.Conversation
import network.loki.messenger.libsession_util.util.KeyPair
import network.loki.messenger.libsession_util.util.Sodium
import network.loki.messenger.libsession_util.util.UserPic
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log

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
        val c = contacts.getOrConstruct(definitelyRealId)
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
        val c2 = contacts2.getOrConstruct(anotherId)
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

    @Test
    fun jni_remove_all_test() {
        val convos = ConversationVolatileConfig.newInstance(keyPair.secretKey)
        assertEquals(0 /* number removed */, convos.eraseAll { true /* 'erase' every item */ })

        val definitelyRealId = "050000000000000000000000000000000000000000000000000000000000000000"
        val definitelyRealConvo = Conversation.OneToOne(definitelyRealId, System.currentTimeMillis(), false)
        convos.set(definitelyRealConvo)

        val anotherDefinitelyReadId = "051111111111111111111111111111111111111111111111111111111111111111"
        val anotherDefinitelyRealConvo = Conversation.OneToOne(anotherDefinitelyReadId, System.currentTimeMillis(), false)
        convos.set(anotherDefinitelyRealConvo)

        assertEquals(2, convos.sizeOneToOnes())

        val numErased = convos.eraseAll { convo ->
            convo is Conversation.OneToOne && convo.sessionId == definitelyRealId
        }
        assertEquals(1, numErased)
        assertEquals(1, convos.sizeOneToOnes())
    }

    @Test
    fun test_open_group_urls() {
        val (base1, room1, pk1) = Conversation.OpenGroup.parseFullUrl(
            "https://example.com/" +
            "SomeRoom?public_key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )!!

        val (base2, room2, pk2) = Conversation.OpenGroup.parseFullUrl(
            "HTTPS://EXAMPLE.COM/" +
            "sOMErOOM?public_key=0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        )!!

        val (base3, room3, pk3) = Conversation.OpenGroup.parseFullUrl(
            "HTTPS://EXAMPLE.COM/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base4, room4, pk4) = Conversation.OpenGroup.parseFullUrl(
            "http://example.com/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base5, room5, pk5) = Conversation.OpenGroup.parseFullUrl(
            "HTTPS://EXAMPLE.com:443/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base6, room6, pk6) = Conversation.OpenGroup.parseFullUrl(
            "HTTP://EXAMPLE.com:80/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base7, room7, pk7) = Conversation.OpenGroup.parseFullUrl(
            "http://example.com:80/r/" +
            "someroom?public_key=ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8"
        )!!
        val (base8, room8, pk8) = Conversation.OpenGroup.parseFullUrl(
            "http://example.com:80/r/" +
            "someroom?public_key=yrtwk3hjixg66yjdeiuauk6p7hy1gtm8tgih55abrpnsxnpm3zzo"
        )!!

        assertEquals("https://example.com", base1)
        assertEquals(base1, base2)
        assertEquals(base1, base3)
        assertNotEquals(base1, base4)
        assertEquals(base4, "http://example.com")
        assertEquals(base1, base5)
        assertEquals(base4, base6)
        assertEquals(base4, base7)
        assertEquals(base4, base8)
        assertEquals(room1, "someroom")
        assertEquals(room2, "someroom")
        assertEquals(room3, "someroom")
        assertEquals(room4, "someroom")
        assertEquals(room5, "someroom")
        assertEquals(room6, "someroom")
        assertEquals(room7, "someroom")
        assertEquals(room8, "someroom")
        assertEquals(Hex.toStringCondensed(pk1), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk2), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk3), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk4), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk5), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk6), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk7), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        assertEquals(Hex.toStringCondensed(pk8), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    }

    @Test
    fun test_conversations() {
        val convos = ConversationVolatileConfig.newInstance(keyPair.secretKey)
        val definitelyRealId = "055000000000000000000000000000000000000000000000000000000000000000"
        assertNull(convos.getOneToOne(definitelyRealId))
        assertTrue(convos.empty())
        assertEquals(0, convos.size())

        val c = convos.getOrConstructOneToOne(definitelyRealId)

        assertEquals(definitelyRealId, c.sessionId)
        assertEquals(0, c.lastRead)

        assertFalse(convos.needsPush())
        assertFalse(convos.needsDump())
        assertEquals(0, convos.push().seqNo)

        val nowMs = System.currentTimeMillis()

        c.lastRead = nowMs

        convos.set(c)

        assertNull(convos.getLegacyClosedGroup(definitelyRealId))
        assertNotNull(convos.getOneToOne(definitelyRealId))
        assertEquals(nowMs, convos.getOneToOne(definitelyRealId)?.lastRead)

        assertTrue(convos.needsPush())
        assertTrue(convos.needsDump())

        val openGroupPubKey = Hex.fromStringCondensed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

        val og = convos.getOrConstructOpenGroup("http://Example.ORG:5678", "SudokuRoom", openGroupPubKey)

        assertEquals("http://example.org:5678", og.baseUrl) // Note: lower-case
        assertEquals("sudokuroom", og.room) // Note: lower-case
        assertEquals(32, og.pubKey.size);
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", og.pubKeyHex)

        og.unread = true

        convos.set(og)

        val (_, seqNo) = convos.push()

        assertEquals(1, seqNo)

        convos.confirmPushed(seqNo)

        assertTrue(convos.needsDump())
        assertFalse(convos.needsPush())

        val convos2 = ConversationVolatileConfig.newInstance(keyPair.secretKey, convos.dump())
        assertFalse(convos.needsPush())
        assertFalse(convos.needsDump())
        assertEquals(1, convos.push().seqNo)
        assertFalse(convos.needsDump())

        val x1 = convos2.getOneToOne(definitelyRealId)!!
        assertEquals(nowMs, x1.lastRead)
        assertEquals(definitelyRealId, x1.sessionId)
        assertEquals(false, x1.unread)

        val x2 = convos2.getOpenGroup("http://EXAMPLE.org:5678", "sudokuRoom", openGroupPubKey)!!
        assertEquals("http://example.org:5678", x2.baseUrl)
        assertEquals("sudokuroom", x2.room)
        assertEquals(x2.pubKeyHex, Hex.toStringCondensed(openGroupPubKey))
        assertTrue(x2.unread)

        val anotherId = "051111111111111111111111111111111111111111111111111111111111111111"
        val c2 = convos.getOrConstructOneToOne(anotherId)
        c2.unread = true
        convos2.set(c2)

        val c3 = convos.getOrConstructLegacyClosedGroup(
            "05cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        )
        c3.lastRead = nowMs - 50
        convos2.set(c3)

        assertTrue(convos2.needsPush())

        val (toPush2, seqNo2) = convos2.push()
        assertEquals(2, seqNo2)

        convos.merge(toPush2)
        convos2.confirmPushed(seqNo2)

        assertFalse(convos.needsPush())
        assertEquals(seqNo2, convos.push().seqNo)

        val seen = mutableListOf<String>()
        for ((ind, conv) in listOf(convos, convos2).withIndex()) {
            Log.e("Test","Testing seen from convo #$ind")
            seen.clear()
            assertEquals(4, conv.size())
            assertEquals(2, conv.sizeOneToOnes())
            assertEquals(1, conv.sizeOpenGroups())
            assertEquals(1, conv.sizeLegacyClosedGroups())
            assertFalse(conv.empty())
            val allConvos = conv.all()
            for (convo in allConvos) {
                when (convo) {
                    is Conversation.OneToOne -> seen.add("1-to-1: ${convo.sessionId}")
                    is Conversation.OpenGroup -> seen.add("og: ${convo.baseUrl}/r/${convo.room}")
                    is Conversation.LegacyClosedGroup -> seen.add("cl: ${convo.groupId}")
                }
            }

            assertTrue(seen.contains("1-to-1: 051111111111111111111111111111111111111111111111111111111111111111"))
            assertTrue(seen.contains("1-to-1: 055000000000000000000000000000000000000000000000000000000000000000"))
            assertTrue(seen.contains("og: http://example.org:5678/r/sudokuroom"))
            assertTrue(seen.contains("cl: 05cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"))
            assertTrue(seen.size == 4) // for some reason iterative checks aren't working in test cases
        }

        assertFalse(convos.needsPush())
        convos.eraseOneToOne("052000000000000000000000000000000000000000000000000000000000000000")
        assertFalse(convos.needsPush())
        convos.eraseOneToOne("055000000000000000000000000000000000000000000000000000000000000000")
        assertTrue(convos.needsPush())

        assertEquals(1, convos.allOneToOnes().size)
        assertEquals("051111111111111111111111111111111111111111111111111111111111111111",
            convos.allOneToOnes().map(Conversation.OneToOne::sessionId).first()
        )
        assertEquals(1, convos.allOpenGroups().size)
        assertEquals("http://example.org:5678",
            convos.allOpenGroups().map(Conversation.OpenGroup::baseUrl).first()
        )
        assertEquals(1, convos.allLegacyClosedGroups().size)
        assertEquals("05cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            convos.allLegacyClosedGroups().map(Conversation.LegacyClosedGroup::groupId).first()
        )
    }

}