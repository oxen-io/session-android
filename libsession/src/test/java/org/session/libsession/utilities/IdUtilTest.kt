package org.session.libsession.utilities

import org.junit.Assert
import org.junit.Test

class IdUtilTest {

    @Test
    fun testTruncate() {
        val testString = "123456789"

        val result = truncateIdForDisplay(testString)

        Assert.assertEquals(result, "1234…6789")
    }

    @Test
    fun testDontTruncateShortMessage() {
        val testString = "not much"

        val result = truncateIdForDisplay(testString)

        Assert.assertEquals(result, "not much")
    }
}
