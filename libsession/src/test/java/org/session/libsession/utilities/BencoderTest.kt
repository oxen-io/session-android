package org.session.libsession.utilities

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeDict
import org.session.libsession.utilities.bencode.BencodeInteger
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.bencode

class BencoderTest {

    @Test
    fun `it should decode a basic string`() {
        val basicString = "5:howdy".toByteArray()
        val bencoder = Bencode.Decoder(basicString)
        val result = bencoder.decode()
        assertEquals("howdy".bencode(), result)
    }

    @Test
    fun `it should decode a basic integer`() {
        val basicInteger = "i3e".toByteArray()
        val bencoder = Bencode.Decoder(basicInteger)
        val result = bencoder.decode()
        assertEquals(BencodeInteger(3), result)
    }

    @Test
    fun `it should decode a list of integers`() {
        val basicIntList = "li1ei2ee".toByteArray()
        val bencoder = Bencode.Decoder(basicIntList)
        val result = bencoder.decode()
        assertEquals(
            BencodeList(
                1.bencode(),
                2.bencode()
            ),
            result
        )
    }

    @Test
    fun `it should decode a basic dict`() {
        val basicDict = "d4:spaml1:a1:bee".toByteArray()
        val bencoder = Bencode.Decoder(basicDict)
        val result = bencoder.decode()
        assertEquals(
            BencodeDict(
                "spam" to BencodeList(
                    "a".bencode(),
                    "b".bencode()
                )
            ),
            result
        )
    }

    @Test
    fun `it should encode a basic string`() {
        val basicString = "5:howdy".toByteArray()
        val element = "howdy".bencode()
        assertArrayEquals(basicString, element.encode())
    }

    @Test
    fun `it should encode a basic int`() {
        val basicInt = "i3e".toByteArray()
        val element = 3.bencode()
        assertArrayEquals(basicInt, element.encode())
    }

    @Test
    fun `it should encode a basic list`() {
        val basicList = "li1ei2ee".toByteArray()
        val element = BencodeList(1.bencode(),2.bencode())
        assertArrayEquals(basicList, element.encode())
    }

    @Test
    fun `it should encode a basic dict`() {
        val basicDict = "d4:spaml1:a1:bee".toByteArray()
        val element = BencodeDict(
            "spam" to BencodeList(
                "a".bencode(),
                "b".bencode()
            )
        )
        assertArrayEquals(basicDict, element.encode())
    }

    @Test
    fun `it should encode a more complex real world case`() {
        val source = "d15:lastReadMessaged66:031122334455667788990011223344556677889900112233445566778899001122i1234568790e66:051122334455667788990011223344556677889900112233445566778899001122i1234568790ee5:seqNoi1ee".toByteArray()
        val result = Bencode.Decoder(source).decode()
        val expected = BencodeDict(
            "lastReadMessage" to BencodeDict(
                "051122334455667788990011223344556677889900112233445566778899001122" to 1234568790.bencode(),
                "031122334455667788990011223344556677889900112233445566778899001122" to 1234568790.bencode()
            ),
            "seqNo" to BencodeInteger(1)
        )
        assertEquals(expected, result)
    }

}