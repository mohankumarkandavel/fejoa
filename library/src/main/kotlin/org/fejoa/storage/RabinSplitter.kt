package org.fejoa.storage

import org.rabinfingerprint.fingerprint.RabinFingerprintLongWindowed
import org.rabinfingerprint.polynomial.Polynomial


class RabinSplitter : DynamicSplitter {
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

    constructor(targetChunkSize: Int, minChunkSize: Int, maxChunkSize: Int, windowSize: Int)
            : super(targetChunkSize, minChunkSize, maxChunkSize, windowSize) {
        this.window = bucket[windowSize]
    }

    constructor(targetChunkSize: Int, minChunkSize: Int, maxChunkSize: Int)
            : super(targetChunkSize, minChunkSize, maxChunkSize) {
        this.window = bucket[windowSize]
    }

    private constructor(splitter: RabinSplitter)
            : super(splitter.targetChunkSize, splitter.minChunkSize, splitter.maxChunkSize, splitter.windowSize){
        this.window = bucket[splitter.windowSize]
    }

    override fun updateFingerprint(byte: Byte): Long {
        window.pushByte(byte)
        return window.fingerprintLong
    }

    override fun resetFingerprint() {
        window.reset()
    }

    override fun newInstance(): ChunkSplitter {
        return RabinSplitter(this)
    }

    companion object {
        private val bucket = WindowBucket()
    }
}
