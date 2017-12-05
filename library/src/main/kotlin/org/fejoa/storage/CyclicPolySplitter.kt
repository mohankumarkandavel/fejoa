package org.fejoa.storage

import org.fejoa.crypto.CryptoHelper
import org.fejoa.support.ByteArrayInStream
import org.fejoa.support.readLong
import org.fejoa.support.write
import org.fejoa.support.assert


class RingByteBuffer(val capacity: Int) {
    private val buffer = ByteArray(capacity)
    // position of the first byte in the queue
    private var startIndex = 0
    // position of the last byte in the queue
    private var endIndex = -1
    private var size = 0

    fun clear() {
        startIndex = 0
        endIndex = -1
        size = 0
    }

    fun push(element: Byte) {
        size++
        assert(size <= capacity)
        endIndex++
        if (endIndex >= capacity)
            endIndex = 0
        buffer[endIndex] = element
    }

    fun pop(): Byte {
        size--
        assert(size >= 0)
        val element = buffer[startIndex]
        startIndex ++
        if (startIndex >= capacity)
            startIndex = 0
        return element
    }

    fun isFull(): Boolean {
        return size == capacity
    }
}


/**
 * Cyclic polynomial splitter
 *
 * Also see: https://en.wikipedia.org/wiki/Rolling_hash#Cyclic_polynomial
 *
 * The hash table is populated using a configurable seed value:
 * h(i) = sha256(seed || i)
 */
class CyclicPolySplitter private constructor(val seed: ByteArray,
                                             private val pushTable: LongArray, private val popTable: LongArray,
                                             targetChunkSize: Int, minChunkSize: Int, maxChunkSize: Int,
                                             windowSize: Int)
    : DynamicSplitter(targetChunkSize, minChunkSize, maxChunkSize, windowSize) {

    var fingerprint: Long = 0L
    private val ringBuffer = RingByteBuffer(windowSize)

    companion object {
        suspend fun create(seed: ByteArray, targetChunkSize: Int, minChunkSize: Int, maxChunkSize: Int,
                           windowSize: Int): CyclicPolySplitter {
            val pushTable = pushTable(seed)
            return CyclicPolySplitter(seed, pushTable, popTable(pushTable, windowSize), targetChunkSize, minChunkSize,
                    maxChunkSize, windowSize)
        }

        suspend private fun pushTable(seed: ByteArray): LongArray {
            val map = LongArray(256)
            val hashStream = CryptoHelper.sha256Hash()
            for (i in 0 until map.size) {
                hashStream.reset()
                hashStream.write(seed)
                hashStream.write(i)
                map[i] = ByteArrayInStream(hashStream.hash()).readLong()
            }
            return map
        }

        fun popTable(pushTable: LongArray, windowSize: Int): LongArray {
            assert(windowSize <= 64, { "Window size must be smaller 64" })

            val map = LongArray(pushTable.size)
            for (i in 0 until map.size)
                map[i] = circularShr(pushTable[i], windowSize)

            return map
        }

        private fun circularShr(number: Long, shift: Int): Long {
            return (number ushr shift) or (number shl (64 - shift))
        }
    }

    override fun newInstance(): ChunkSplitter {
        return CyclicPolySplitter(seed, pushTable, popTable, targetChunkSize, minChunkSize, maxChunkSize, windowSize)
    }

    /**
     * 1) circular shift the existing fingerprint to the right
     * 2) if full remove the contribution of the oldest byte
     * 3) add the new byte
     */
    override fun updateFingerprint(byte: Byte): Long {
        fingerprint = circularShr(fingerprint, 1)

        if (ringBuffer.isFull()) {
            val removedByte = ringBuffer.pop()
            fingerprint = fingerprint xor popTable[removedByte.toInt() and 0xFF]
        }
        ringBuffer.push(byte)
        fingerprint = fingerprint xor pushTable[byte.toInt() and 0xFF]
        return fingerprint
    }

    override fun resetFingerprint() {
        fingerprint = 0
        ringBuffer.clear()
    }
}