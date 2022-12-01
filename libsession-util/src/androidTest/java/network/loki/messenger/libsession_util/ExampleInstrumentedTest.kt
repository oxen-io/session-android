package network.loki.messenger.libsession_util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("network.loki.messenger.libsession_util.test", appContext.packageName)
    }

    @Test
    fun jni_accessible() {
        val userProfile = UserProfile.newInstance()
        assertNotNull(userProfile)
        userProfile.free()
    }

    @Test
    fun jni_user_profile_c_api() {
        val userProfile = UserProfile.newInstance()

        assertFalse(userProfile.needsPush())
        assertFalse(userProfile.needsDump())

        val name = userProfile.getName()
        assertNull(name)

        val (toPush, seqNo) = userProfile.push()
        assertEquals("d1:#i0e1:&de1:<le1:=dee", toPush)
        assertEquals(0, seqNo)

        userProfile.free()
    }

    @Test
    fun jni_setting_getting() {
        val userProfile = UserProfile.newInstance()
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