package org.session.libsignal.utilities

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SnodeVersionTest(
    private val v1: String,
    private val v2: String,
    private val expectedEqual: Boolean,
    private val expectedLessThan: Boolean
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: testVersion({0},{1}) = (equalTo: {2}, lessThan: {3})")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("1", "1", true, false),
            arrayOf("1", "2", false, true),
            arrayOf("2", "1", false, false),
            arrayOf("1.0", "1", true, false),
            arrayOf("1.0", "1.0.0", true, false),
            arrayOf("1.0", "1.0.0.0", true, false),
            arrayOf("1.0", "1.0.0.0.0.0", true, false),
            arrayOf("2.0", "1.2", false, false),
            arrayOf("1.0.0.0", "1.0.0.1", false, true),
            // Snode.Version only considers the first 4 integers, so these are equal
            arrayOf("1.0.0.0", "1.0.0.0.1", true, false),
            arrayOf("1.0.0.1", "1.0.0.1", true, false),
            // parts can be up to 16 bits, around 65,535
            arrayOf("65535.65535.65535.65535", "65535.65535.65535.65535", true, false),
            // values higher than this are coerced to 65535 (:
            arrayOf("65535.65535.65535.65535", "65535.65535.65535.99999", true, false),
        )
    }

    @Test
    fun testVersionEqual() {
        val version1 = Snode.Version(v1)
        val version2 = Snode.Version(v2)
        assertThat(version1 == version2, equalTo(expectedEqual))
    }

    @Test
    fun testVersionOnePartLessThan() {
        val version1 = Snode.Version(v1)
        val version2 = Snode.Version(v2)
        assertThat(version1 < version2, equalTo(expectedLessThan))
    }
}