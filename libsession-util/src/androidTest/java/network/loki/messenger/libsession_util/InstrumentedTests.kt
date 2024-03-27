package network.loki.messenger.libsession_util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.libsession_util.util.*
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
    fun testDirtyEmptyString() {
        val contacts = Contacts.newInstance(keyPair.secretKey)
        val definitelyRealId = "050000000000000000000000000000000000000000000000000000000000000000"
        val contact = contacts.getOrConstruct(definitelyRealId)
        contacts.set(contact)
        assertTrue(contacts.dirty())
        contacts.set(contact.copy(name = "test"))
        assertTrue(contacts.dirty())
        val push = contacts.push()
        contacts.confirmPushed(push.seqNo, "abc123")
        contacts.dump()
        contacts.set(contact.copy(name = "test2"))
        contacts.set(contact.copy(name = "test"))
        assertTrue(contacts.dirty())
    }

    @Test
    fun jni_contacts() {
        val contacts = Contacts.newInstance(keyPair.secretKey)
        val definitelyRealId = "050000000000000000000000000000000000000000000000000000000000000000"
        assertNull(contacts.get(definitelyRealId))

        // Should be an uninitialized contact apart from ID
        val c = contacts.getOrConstruct(definitelyRealId)
        assertEquals(definitelyRealId, c.id)
        assertTrue(c.name.isEmpty())
        assertTrue(c.nickname.isEmpty())
        assertFalse(c.approved)
        assertFalse(c.approvedMe)
        assertFalse(c.blocked)
        assertEquals(UserPic.DEFAULT, c.profilePicture)

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
        assertEquals(UserPic.DEFAULT, cSaved.profilePicture)

        val push1 = contacts.push()

        assertEquals(1, push1.seqNo)
        contacts.confirmPushed(push1.seqNo, "fakehash1")
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
        contacts2.confirmPushed(push2.seqNo, "fakehash2")
        assertFalse(contacts2.needsPush())

        contacts.merge("fakehash2" to push2.config)


        assertFalse(contacts.needsPush())
        assertEquals(push2.seqNo, contacts.push().seqNo)

        val contactList = contacts.all().toList()
        assertEquals(definitelyRealId, contactList[0].id)
        assertEquals(anotherId, contactList[1].id)
        assertEquals("Joey", contactList[0].nickname)
        assertEquals("", contactList[1].nickname)

        contacts.erase(definitelyRealId)

        val thirdId ="052222222222222222222222222222222222222222222222222222222222222222"
        val third = Contact(
            id = thirdId,
            nickname = "Nickname 3",
            approved = true,
            blocked = true,
            profilePicture = UserPic("http://example.com/huge.bmp", "qwertyuio01234567890123456789012".encodeToByteArray()),
            expiryMode = ExpiryMode.NONE
        )
        contacts2.set(third)
        assertTrue(contacts.needsPush())
        assertTrue(contacts2.needsPush())
        val toPush = contacts.push()
        val toPush2 = contacts2.push()
        assertEquals(toPush.seqNo, toPush2.seqNo)
        assertThat(toPush2.config, not(equals(toPush.config)))

        contacts.confirmPushed(toPush.seqNo, "fakehash3a")
        contacts2.confirmPushed(toPush2.seqNo, "fakehash3b")

        contacts.merge("fakehash3b" to toPush2.config)
        contacts2.merge("fakehash3a" to toPush.config)

        assertTrue(contacts.needsPush())
        assertTrue(contacts2.needsPush())

        val mergePush = contacts.push()
        val mergePush2 = contacts2.push()

        assertEquals(mergePush.seqNo, mergePush2.seqNo)
        assertArrayEquals(mergePush.config, mergePush2.config)

        assertTrue(mergePush.obsoleteHashes.containsAll(listOf("fakehash3b", "fakehash3a")))
        assertTrue(mergePush2.obsoleteHashes.containsAll(listOf("fakehash3b", "fakehash3a")))

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
        assertEquals(UserPic.DEFAULT, userProfile.getPic())

        // Now let's go set a profile name and picture:
        // not sending keylen like c api so cutting off the NOTSECRET in key for testing purposes
        userProfile.setName("Kallie")
        val newUserPic = UserPic("http://example.org/omg-pic-123.bmp", "secret78901234567890123456789012".encodeToByteArray())
        userProfile.setPic(newUserPic)
        userProfile.setNtsPriority(9)

        // Retrieve them just to make sure they set properly:
        assertEquals("Kallie", userProfile.getName())
        val pic = userProfile.getPic()
        assertEquals("http://example.org/omg-pic-123.bmp", pic.url)
        assertEquals("secret78901234567890123456789012", pic.key.decodeToString())

        // Since we've made changes, we should need to push new config to the swarm, *and* should need
        // to dump the updated state:
        assertTrue(userProfile.needsPush())
        assertTrue(userProfile.needsDump())
        val (newToPush, newSeqNo) = userProfile.push()

        val expHash0 =
            Hex.fromStringCondensed("ea173b57beca8af18c3519a7bbf69c3e7a05d1c049fa9558341d8ebb48b0c965")

        val expectedPush1Decrypted = ("d" +
                "1:#"+ "i1e" +
                "1:&"+ "d"+
                "1:+"+ "i9e"+
                "1:n"+ "6:Kallie"+
                "1:p"+ "34:http://example.org/omg-pic-123.bmp"+
                "1:q"+ "32:secret78901234567890123456789012"+
                "e"+
                "1:<"+ "l"+
                "l"+ "i0e"+ "32:").encodeToByteArray() + expHash0 + ("de"+ "e"+
                "e"+
                "1:="+ "d"+
                "1:+" +"0:"+
                "1:n" +"0:"+
                "1:p" +"0:"+
                "1:q" +"0:"+
                "e"+
                "e").encodeToByteArray()

        assertEquals(1, newSeqNo)
        // We haven't dumped, so still need to dump:
        assertTrue(userProfile.needsDump())
        // We did call push but we haven't confirmed it as stored yet, so this will still return true:
        assertTrue(userProfile.needsPush())

        val dump = userProfile.dump()
        // (in a real client we'd now store this to disk)
        assertFalse(userProfile.needsDump())
        val expectedDump = ("d" +
                "1:!"+ "i2e" +
                "1:$").encodeToByteArray() + expectedPush1Decrypted.size.toString().encodeToByteArray() +
                ":".encodeToByteArray() + expectedPush1Decrypted +
                "1:(0:1:)le".encodeToByteArray()+
                "e".encodeToByteArray()

        assertArrayEquals(expectedDump, dump)

        userProfile.confirmPushed(newSeqNo, "fakehash1")

        val newConf = UserProfile.newInstance(edSk)

        val accepted = newConf.merge("fakehash1" to newToPush)
        assertEquals(1, accepted)

        assertTrue(newConf.needsDump())
        assertFalse(newConf.needsPush())
        val _ignore = newConf.dump()
        assertFalse(newConf.needsDump())


        userProfile.setName("Raz")
        newConf.setName("Nibbler")
        newConf.setPic(UserPic("http://new.example.com/pic", "qwertyuio01234567890123456789012".encodeToByteArray()))

        val conf = userProfile.push()
        val conf2 = newConf.push()

        userProfile.confirmPushed(conf.seqNo, "fakehash2")
        newConf.confirmPushed(conf2.seqNo, "fakehash3")

        userProfile.dump()

        assertFalse(conf.config.contentEquals(conf2.config))

        newConf.merge("fakehash2" to conf.config)
        userProfile.merge("fakehash3" to conf2.config)

        assertTrue(newConf.needsPush())
        assertTrue(userProfile.needsPush())

        val newSeq1 = userProfile.push()

        assertEquals(3, newSeq1.seqNo)

        userProfile.confirmPushed(newSeq1.seqNo, "fakehash4")

        // assume newConf push gets rejected as it was last to write and clear previous config by hash on oxenss
        newConf.merge("fakehash4" to newSeq1.config)

        val newSeqMerge = newConf.push()

        newConf.confirmPushed(newSeqMerge.seqNo, "fakehash5")

        assertEquals("Raz", newConf.getName())
        assertEquals(3, newSeqMerge.seqNo)

        // userProfile device polls and merges
        userProfile.merge("fakehash5" to newSeqMerge.config)

        val userConfigMerge = userProfile.push()

        assertEquals(3, userConfigMerge.seqNo)

        assertEquals("Raz", newConf.getName())
        assertEquals("Raz", userProfile.getName())

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
        a.confirmPushed(aSeq, "hashfroma")
        b.setName("B")
        // polls and sees invalid state, has to merge
        b.merge("hashfroma" to aPush)
        val (bPush, bSeq) = b.push()
        b.confirmPushed(bSeq, "hashfromb")
        assertEquals("B", b.getName())
        assertEquals(1, aSeq)
        assertEquals(2, bSeq)
        a.merge("hashfromb" to bPush)
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
        val (base1, room1, pk1) = BaseCommunityInfo.parseFullUrl(
            "https://example.com/" +
            "someroom?public_key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )!!

        val (base2, room2, pk2) = BaseCommunityInfo.parseFullUrl(
            "HTTPS://EXAMPLE.COM/" +
            "someroom?public_key=0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        )!!

        val (base3, room3, pk3) = BaseCommunityInfo.parseFullUrl(
            "HTTPS://EXAMPLE.COM/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base4, room4, pk4) = BaseCommunityInfo.parseFullUrl(
            "http://example.com/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base5, room5, pk5) = BaseCommunityInfo.parseFullUrl(
            "HTTPS://EXAMPLE.com:443/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base6, room6, pk6) = BaseCommunityInfo.parseFullUrl(
            "HTTP://EXAMPLE.com:80/r/" +
            "someroom?public_key=0123456789aBcdEF0123456789abCDEF0123456789ABCdef0123456789ABCDEF"
        )!!

        val (base7, room7, pk7) = BaseCommunityInfo.parseFullUrl(
            "http://example.com:80/r/" +
            "someroom?public_key=ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8"
        )!!
        val (base8, room8, pk8) = BaseCommunityInfo.parseFullUrl(
            "http://example.com:80/r/" +
            "someroom?public_key=yrtwk3hjixg66yjdeiuauk6p7hy1gtm8tgih55abrpnsxnpm3zzo"
        )!!

        assertEquals("https://example.com", base1)
        assertEquals("http://example.com", base4)
        assertEquals(base1, base2)
        assertEquals(base1, base3)
        assertNotEquals(base1, base4)
        assertEquals(base1, base5)
        assertEquals(base4, base6)
        assertEquals(base4, base7)
        assertEquals(base4, base8)
        assertEquals("someroom", room1)
        assertEquals("someroom", room2)
        assertEquals("someroom", room3)
        assertEquals("someroom", room4)
        assertEquals("someroom", room5)
        assertEquals("someroom", room6)
        assertEquals("someroom", room7)
        assertEquals("someroom", room8)
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

        val og = convos.getOrConstructCommunity("http://Example.ORG:5678", "SudokuRoom", openGroupPubKey)
        val ogCommunity = og.baseCommunityInfo

        assertEquals("http://example.org:5678", ogCommunity.baseUrl) // Note: lower-case
        assertEquals("sudokuroom", ogCommunity.room) // Note: lower-case
        assertEquals(64, ogCommunity.pubKeyHex.length)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", ogCommunity.pubKeyHex)

        og.unread = true

        convos.set(og)

        val (_, seqNo) = convos.push()

        assertEquals(1, seqNo)

        convos.confirmPushed(seqNo, "fakehash1")

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

        val x2 = convos2.getCommunity("http://EXAMPLE.org:5678", "sudokuRoom")!!
        val x2Info = x2.baseCommunityInfo
        assertEquals("http://example.org:5678", x2Info.baseUrl)
        assertEquals("sudokuroom", x2Info.room)
        assertEquals(x2Info.pubKeyHex, Hex.toStringCondensed(openGroupPubKey))
        assertTrue(x2.unread)

        val anotherId = "051111111111111111111111111111111111111111111111111111111111111111"
        val c2 = convos.getOrConstructOneToOne(anotherId)
        c2.unread = true
        convos2.set(c2)

        val c3 = convos.getOrConstructLegacyGroup(
            "05cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        )
        c3.lastRead = nowMs - 50
        convos2.set(c3)

        assertTrue(convos2.needsPush())

        val (toPush2, seqNo2) = convos2.push()
        assertEquals(2, seqNo2)

        convos2.confirmPushed(seqNo2, "fakehash2")
        convos.merge("fakehash2" to toPush2)

        assertFalse(convos.needsPush())
        assertEquals(seqNo2, convos.push().seqNo)

        val seen = mutableListOf<String>()
        for ((ind, conv) in listOf(convos, convos2).withIndex()) {
            Log.e("Test","Testing seen from convo #$ind")
            seen.clear()
            assertEquals(4, conv.size())
            assertEquals(2, conv.sizeOneToOnes())
            assertEquals(1, conv.sizeCommunities())
            assertEquals(1, conv.sizeLegacyClosedGroups())
            assertFalse(conv.empty())
            val allConvos = conv.all()
            for (convo in allConvos) {
                when (convo) {
                    is Conversation.OneToOne -> seen.add("1-to-1: ${convo.sessionId}")
                    is Conversation.Community -> seen.add("og: ${convo.baseCommunityInfo.baseUrl}/r/${convo.baseCommunityInfo.room}")
                    is Conversation.LegacyGroup -> seen.add("cl: ${convo.groupId}")
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
        assertEquals(1, convos.allCommunities().size)
        assertEquals("http://example.org:5678",
            convos.allCommunities().map { it.baseCommunityInfo.baseUrl }.first()
        )
        assertEquals(1, convos.allLegacyClosedGroups().size)
        assertEquals("05cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            convos.allLegacyClosedGroups().map(Conversation.LegacyGroup::groupId).first()
        )
    }

}