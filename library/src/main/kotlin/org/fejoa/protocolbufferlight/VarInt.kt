package org.fejoa.protocolbufferlight

import org.fejoa.support.*


/**
 * Class for efficient serialization of potentially small numbers.
 *
 * Format: The most significant bit in each byte indicates if more bytes are following. This is the same MSB encoding
 * as described in
 * http://git.kernel.org/cgit/git/git.git/tree/Documentation/technical/pack-format.txt?id=HEAD
 * is used.
 *
 * Extra bits: It is possible to squeeze some additional bits into the first byte. This is, for example, useful when
 * storing some flags in combination with a small number. The number of extra bits is limited to 7. However, it only
 * makes sense to store 6 or less bits since otherwise there is no storage advantage, i.e. the bits can directly be
 * stored in a single byte.
 *
 * Also see the figure on:
 * https://stackoverflow.com/questions/9478023/is-the-git-binary-diff-algorithm-delta-storage-standardized
 *
 * Example:
 * |1|extra|num_0|
 * |1|   num_1   |
 * \0|   num_2   |
 *
 * The stored number is the concatenation of |num_2|num_1|num_0| where num_2 is the most significant part of the number.
 */
object VarInt {
    val MSB_MASK = 0x1 shl 7

    /**
     *
     * @param inputStream
     * @param bitsToUseFromFirstByte the number of lower bits to use from the first byte
     * @return the read number (first), the bytes read (second) and the extra bits (third)
     */
    fun read(inputStream: InStream, extraSize: Int): Triple<Long, Int, Int> {
        if (extraSize < 0 || extraSize > 7)
            throw IllegalArgumentException("extraSize must be smaller 8")

        val firstByte = inputStream.read()
        if (firstByte == -1)
            throw EOFException()
        var bytesRead = 1
        var hasNextByte = mostSignificantBitSet(firstByte)
        val firstByteNumSize = 7 - extraSize
        // extract extra data
        var extra: Int
        extra = if (extraSize > 0)
            ((firstByte and MSB_MASK.inv()) shr firstByteNumSize)
        else
            0

        var value = (firstByte and (0xFF shr (extraSize + 1))).toLong()
        var counter = firstByteNumSize
        while (hasNextByte) {
            val byte = inputStream.read()
            if (byte == -1)
                throw EOFException()
            bytesRead++
            hasNextByte = mostSignificantBitSet(byte)
            val numData = byte and MSB_MASK.inv()
            value = value or (numData.toLong() shl counter)
            counter += 7
        }
        return Triple(value, bytesRead, extra)
    }

    suspend fun read(inStream: AsyncInStream): Pair<Long, Int> {
        val result = read(inStream, 0)
        return result.first to result.second
    }

    suspend fun read(inStream: AsyncInStream, extraSize: Int): Triple<Long, Int, Int> {
        if (extraSize < 0 || extraSize > 7)
            throw IllegalArgumentException("extraSize must be smaller 8")

        val firstByte = inStream.readByte().toInt() and 0xFF
        var bytesRead = 1
        var hasNextByte = mostSignificantBitSet(firstByte)
        val firstByteNumSize = 7 - extraSize
        // extract extra data
        var extra: Int
        extra = if (extraSize > 0)
            ((firstByte and MSB_MASK.inv()) shr firstByteNumSize)
        else
            0

        var value = (firstByte and (0xFF shr (extraSize + 1))).toLong()
        var counter = firstByteNumSize
        while (hasNextByte) {
            val byte = inStream.readByte().toInt() and 0xFF
            bytesRead++
            hasNextByte = mostSignificantBitSet(byte)
            val numData = byte and MSB_MASK.inv()
            value = value or (numData.toLong() shl counter)
            counter += 7
        }
        return Triple(value, bytesRead, extra)
    }

    //@Throw(IOException::class)
    fun read(inputStream: InStream): Pair<Long, Int> {
        val result = read(inputStream, 0)
        return result.first to result.second
    }

    private fun mostSignificantBitSet(byte: Int): Boolean {
        return (byte and MSB_MASK) != 0
    }

    private fun setMostSignificantBit(byte: Int): Int {
        return byte or (0x1 shl 7)
    }

    fun write(outputStream: OutStream, number: Int): Int {
        return write(outputStream, number.toLong())
    }

    fun write(outputStream: OutStream, number: Long): Int {
        return write(outputStream, number, 0, 0)
    }

    //@Throw(IOException::class)
    fun write(outputStream: OutStream, number: Long, extra: Int, extraSize: Int): Int {
        if (extraSize < 0 || extraSize > 7)
            throw IllegalArgumentException("extraSize must be smaller 8")

        var remaining = number
        val sanitizedExtra = (extra and (0xFF shr (8 - extraSize)))

        // write first byte including the extra bits
        var firstByte = sanitizedExtra shl (7 - extraSize)
        firstByte = firstByte or (remaining and (0xFF shr 1 + extraSize).toLong()).toInt()
        remaining = remaining shr (7 - extraSize)
        if (remaining != 0L)
            firstByte = setMostSignificantBit(firstByte)
        outputStream.writeByte(firstByte)
        var bytesWritten = 1

        // write remaining
        while (remaining != 0L) {
            var byte = (remaining and (0xFF shr 1)).toInt()
            remaining = remaining shr 7
            if (remaining != 0L)
                byte = setMostSignificantBit(byte)
            outputStream.writeByte(byte)
            bytesWritten++
        }
        return bytesWritten
    }

    suspend fun write(outStream: AsyncOutStream, number: Int): Int {
        return write(outStream, number.toLong())
    }

    suspend fun write(outStream: AsyncOutStream, number: Long): Int {
        return write(outStream, number, 0, 0)
    }

    //@Throw(IOException::class)
    suspend fun write(outStream: AsyncOutStream, number: Long, extra: Int, extraSize: Int): Int {
        if (extraSize < 0 || extraSize > 7)
            throw IllegalArgumentException("extraSize must be smaller 8")

        var remaining = number
        val sanitizedExtra = (extra and (0xFF shr (8 - extraSize)))

        // write first byte including the extra bits
        var firstByte = sanitizedExtra shl (7 - extraSize)
        firstByte = firstByte or (remaining and (0xFF shr 1 + extraSize).toLong()).toInt()
        remaining = remaining shr (7 - extraSize)
        if (remaining != 0L)
            firstByte = setMostSignificantBit(firstByte)
        outStream.write(firstByte)
        var bytesWritten = 1

        // write remaining
        while (remaining != 0L) {
            var byte = (remaining and (0xFF shr 1)).toInt()
            remaining = remaining shr 7
            if (remaining != 0L)
                byte = setMostSignificantBit(byte)
            outStream.write(byte)
            bytesWritten++
        }
        return bytesWritten
    }
}
