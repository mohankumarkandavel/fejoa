package org.fejoa.storage

import org.rabinfingerprint.fingerprint.RabinFingerprintLongWindowed
import org.rabinfingerprint.polynomial.Polynomial


class RabinSplitter : ChunkSplitter {
    // Init the window is expensive so cache it this bucket and reuse it to create a new window.
    private class WindowBucket {
        private val bucket = HashMap<Int, RabinFingerprintLongWindowed>()

        operator fun get(windowSize: Int): RabinFingerprintLongWindowed {
            var window: RabinFingerprintLongWindowed? = bucket[windowSize]
            if (window != null)
                return RabinFingerprintLongWindowed(window)
            window = RabinFingerprintLongWindowed(Polynomial.createFromLong(9256118209264353L), windowSize)
            bucket.put(windowSize, window)
            return window
        }
    }

    private val window: RabinFingerprintLongWindowed

    val targetChunkSize: Int
    private val windowSize = 48
    private var chunkSize: Long = 0
    val minChunkSize: Int
    val maxChunkSize: Int

    constructor(targetChunkSize: Int = CHUNK_8KB, minChunkSize: Int = 128, maxChunkSize: Int = Int.MAX_VALUE) {
        this.targetChunkSize = targetChunkSize
        this.minChunkSize = minChunkSize
        this.maxChunkSize = maxChunkSize
        this.window = bucket[windowSize]
    }

    private constructor(splitter: RabinSplitter) {
        this.targetChunkSize = splitter.targetChunkSize
        this.minChunkSize = splitter.minChunkSize
        this.maxChunkSize = splitter.maxChunkSize
        this.window = bucket[splitter.windowSize]
    }

    override protected fun writeInternal(i: Byte): Boolean {
        chunkSize++
        if (chunkSize < minChunkSize - windowSize)
            return false
        window.pushByte(i)
        if (chunkSize < minChunkSize)
            return false
        if (chunkSize >= maxChunkSize)
            return true
        return if (window.fingerprintLong and MASK < MASK / targetChunkSize) true else false
    }

    override protected fun resetInternal() {
        chunkSize = 0
        window.reset()
    }

    override fun newInstance(): ChunkSplitter {
        return RabinSplitter(this)
    }

    companion object {

        private val bucket = WindowBucket()

        val CHUNK_1KB = 1024
        val CHUNK_8KB = 8 * CHUNK_1KB
        val CHUNK_16KB = 16 * CHUNK_1KB
        val CHUNK_32KB = 32 * CHUNK_1KB
        val CHUNK_64KB = 64 * CHUNK_1KB
        val CHUNK_128KB = 128 * CHUNK_1KB
        private val MASK = 0xFFFFFFFFL
    }
}
