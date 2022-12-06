package network.loki.messenger.libsession_util

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.libsession_util.util.Sodium
import network.loki.messenger.libsession_util.util.UserPic
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

    val seed = Hex.fromStringCondensed("0123456789abcdef0123456789abcdef00000000000000000000000000000000")
    val keyPair = Sodium.ed25519KeyPair(seed)

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("network.loki.messenger.libsession_util.test", appContext.packageName)
    }

    @Test
    fun jni_accessible() {
        val userProfile = UserProfile.newInstance(keyPair.secretKey)
        assertNotNull(userProfile)
        userProfile.free()
    }

    @Test
    fun jni_user_profile_c_api() {
        val userProfile = UserProfile.newInstance(keyPair.secretKey)

        // these should be false as empty config
        assertFalse(userProfile.needsPush())
        assertFalse(userProfile.needsDump())

        // Since it's empty there shouldn't be a name
        assertNull(userProfile.getName())

        // Don't need to push yet so this is just for testing
        val (toPush, seqNo) = userProfile.push()
        assertEquals("d1:#i0e1:&de1:<le1:=dee", toPush.decodeToString())
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

        val expHash0 = Hex.fromStringCondensed("ea173b57beca8af18c3519a7bbf69c3e7a05d1c049fa9558341d8ebb48b0c965")
        val expectedPush1 = ("d" +
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

        assertEquals(1, newSeqNo)
        assertArrayEquals(expectedPush1, newToPush)
        // We haven't dumped, so still need to dump:
        assertTrue(userProfile.needsDump())
        // We did call push but we haven't confirmed it as stored yet, so this will still return true:
        assertTrue(userProfile.needsPush())

        val dump = userProfile.dump()
        // (in a real client we'd now store this to disk)
        assertFalse(userProfile.needsDump())
        val expectedDump = ("d" +
                "1:!i2e" +
                "1:$").encodeToByteArray() + expectedPush1.size.toString().encodeToByteArray() +
                ":".encodeToByteArray() + expectedPush1 +
                "e".encodeToByteArray()

        assertArrayEquals(expectedDump, dump)

        userProfile.free()
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