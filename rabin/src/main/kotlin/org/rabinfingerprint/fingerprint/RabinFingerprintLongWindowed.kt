package org.rabinfingerprint.fingerprint

import org.fejoa.support.valueOf

import org.rabinfingerprint.datastructures.CircularByteQueue
import org.rabinfingerprint.fingerprint.Fingerprint.WindowedFingerprint
import org.rabinfingerprint.polynomial.Polynomial

class RabinFingerprintLongWindowed : RabinFingerprintLong, WindowedFingerprint<Polynomial> {

    protected val byteWindow: CircularByteQueue
    protected val bytesPerWindow: Int
    protected val popTable: LongArray

    constructor(poly: Polynomial, bytesPerWindow: Int) : super(poly) {
        this.bytesPerWindow = bytesPerWindow
        this.byteWindow = CircularByteQueue(bytesPerWindow + 1)
        this.popTable = LongArray(256)
        precomputePopTable()
    }

    constructor(that: RabinFingerprintLongWindowed) : super(that) {
        this.bytesPerWindow = that.bytesPerWindow
        this.byteWindow = CircularByteQueue(bytesPerWindow + 1)
        this.popTable = that.popTable
    }

    private fun precomputePopTable() {
        for (i in 0..255) {
            var f = Polynomial.createFromLong(i.toLong())
            f = f.shiftLeft(valueOf((bytesPerWindow * 8).toLong()))
            f = f.mod(poly)
            popTable[i] = f.toBigInteger().toLong()
        }
    }

    override fun pushBytes(bytes: ByteArray) {
        for (b in bytes) {
            val j = (fingerprintLong shr shift and 0x1FF).toInt()
            fingerprintLong = fingerprintLong shl 8 or (b.toLong() and 0xFF) xor pushTable[j]
            byteWindow.add(b)
            if (byteWindow.isFull) popByte()
        }
    }

    override fun pushBytes(bytes: ByteArray, offset: Int, length: Int) {
        val max = offset + length
        var i = offset
        while (i < max) {
            val b = bytes[i++]
            val j = (fingerprintLong shr shift and 0x1FF).toInt()
            fingerprintLong = fingerprintLong shl 8 or (b.toLong() and 0xFF) xor pushTable[j]
            byteWindow.add(b)
            if (byteWindow.isFull) popByte()
        }
    }

    override fun pushByte(b: Byte) {
        val j = (fingerprintLong shr shift and 0x1FF).toInt()
        fingerprintLong = fingerprintLong shl 8 or (b.toLong() and 0xFF) xor pushTable[j]
        byteWindow.add(b)
        if (byteWindow.isFull) popByte()
    }

    /**
     * Removes the contribution of the first byte in the byte queue from the
     * fingerprint.
     *
     * [RabinFingerprintPolynomial.popByte]
     */
    override fun popByte() {
        val b = byteWindow.poll()
        fingerprintLong = fingerprintLong xor popTable[b.toInt() and 0xFF]
    }

    override fun reset() {
        super.reset()
        byteWindow.clear()
    }
}
