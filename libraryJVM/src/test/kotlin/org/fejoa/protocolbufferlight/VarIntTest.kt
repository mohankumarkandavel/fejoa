package org.fejoa.protocolbufferlight

import org.fejoa.support.ByteArrayInStream
import org.fejoa.support.ByteArrayOutStream
import kotlin.test.Test

import kotlin.experimental.or
import kotlin.test.assertEquals


class VarIntTest {

    private fun assertParsing(data: ByteArray, expected: Long, extra: Int, extraSize: Int) {
        val inputStream = ByteArrayInStream(data)
        val pair = VarInt.read(inputStream, extraSize)
        assertEquals(expected, pair.first)
        assertEquals(extra, pair.third)
    }

    private fun assertParsing(data: ByteArray, expected: Long) {
        val inputStream = ByteArrayInStream(data)
        assertEquals(expected, VarInt.read(inputStream).first)
    }

    private fun assertWriteAndParsing(number: Long, extra: Int, extraSize: Int) {
        val outputStream = ByteArrayOutStream()
        VarInt.write(outputStream, number, extra, extraSize)
        val out = outputStream.toByteArray()
        assertParsing(out, number, extra, extraSize)
    }

    private fun assertWriteAndParsing(number: Long) {
        val outputStream = ByteArrayOutStream()
        VarInt.write(outputStream, number)
        val out = outputStream.toByteArray()
        assertParsing(out, number)
    }

    @Test
    fun testSimple() {
        val data = ByteArray(2)
        data[0] = 2
        assertParsing(data, 2)

        data[0] = 120
        assertParsing(data, 120)

        data[0] = 13
        data[0] = (data[0] or (0x1 shl 6).toByte())
        assertParsing(data, 13, 1, 1)

        data[0] = 0
        data[0] = (data[0] or (0x1 shl 7).toByte())
        data[1] = 1
        assertParsing(data, 128)

        data[0] = 0
        data[0] = (data[0] or (0x1 shl 7).toByte())
        data[0] = (data[0] or (0x1 shl 6))
        data[1] = 1
        assertParsing(data, 192)
    }

    @Test
    fun testNumbers() {
        assertWriteAndParsing(0)

        assertWriteAndParsing(13)

        assertWriteAndParsing(13, 63, 6)

        assertWriteAndParsing(500)

        assertWriteAndParsing(500, 10, 4)

        assertWriteAndParsing(16400)

        assertWriteAndParsing(Int.MAX_VALUE.toLong() * 2L)

        assertWriteAndParsing(Long.MAX_VALUE)

        assertWriteAndParsing(Int.MAX_VALUE.toLong(), 15, 4)

        assertWriteAndParsing(Int.MAX_VALUE.toLong() * 2L, 10, 4)

        assertWriteAndParsing(Long.MAX_VALUE, 4, 4)
    }
}
