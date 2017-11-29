package org.rabinfingerprint.fingerprint

import org.rabinfingerprint.polynomial.Polynomial

/**
 * A [Fingerprint] builder that uses longs and lookup tables to increase
 * performance.
 *
 * Note, the polynomial must be of degree 64 - 8 - 1 - 1 = 54 or less!
 *
 * <pre>
 * 64 for the size of a long
 * 8 for the space we need when shifting
 * 1 for the sign bit (Java doesn't support unsigned longs)
 * 1 for the conversion between degree and bit offset.
</pre> *
 *
 * Some good choices are 53, 47, 31, 15
 *
 * @see RabinFingerprintPolynomial for a rundown of the math
 */
open class RabinFingerprintLong : AbstractFingerprint {
    protected val pushTable: LongArray
    protected val degree: Int
    protected val shift: Int

    var fingerprintLong: Long = 0
        protected set

    constructor(poly: Polynomial) : super(poly) {
        this.degree = poly.degree().toInt()
        this.shift = degree - 8
        this.fingerprintLong = 0
        this.pushTable = LongArray(512)
        precomputePushTable()
    }

    constructor(that: RabinFingerprintLong) : super(that.poly) {
        this.degree = that.degree
        this.shift = that.shift
        this.pushTable = that.pushTable
        this.fingerprintLong = 0
    }

    /**
     * Precomputes the results of pushing and popping bytes. These use the more
     * accurate Polynomial methods (they won't overflow like longs, and they
     * compute in GF(2^k)).
     *
     * These algorithms should be synonymous with
     * [RabinFingerprintPolynomial.pushByte] and
     * [RabinFingerprintPolynomial.popByte], but the results are stored to
     * be xor'red with the fingerprint in the inner loop of our own
     * [.pushByte] and [.popByte]
     */
    private fun precomputePushTable() {
        for (i in 0..511) {
            var f = Polynomial.createFromLong(i.toLong())
            f = f.shiftLeft(poly.degree())
            f = f.xor(f.mod(poly))
            pushTable[i] = f.toBigInteger().toLong()
        }
    }

    override fun pushBytes(bytes: ByteArray) {
        for (b in bytes) {
            val j = (fingerprintLong shr shift and 0x1FF).toInt()
            fingerprintLong = fingerprintLong shl 8 or (b.toLong() and 0xFF) xor pushTable[j]
        }
    }

    override fun pushBytes(bytes: ByteArray, offset: Int, length: Int) {
        val max = offset + length
        var i = offset
        while (i < max) {
            val j = (fingerprintLong shr shift and 0x1FF).toInt()
            fingerprintLong = fingerprintLong shl 8 or (bytes[i++].toLong() and 0xFF) xor pushTable[j]
        }
    }

    override fun pushByte(b: Byte) {
        val j = (fingerprintLong shr shift and 0x1FF).toInt()
        fingerprintLong = fingerprintLong shl 8 or (b.toLong() and 0xFF) xor pushTable[j]
    }

    override fun reset() {
        this.fingerprintLong = 0L
    }

    override val fingerprint: Polynomial
        get() = Polynomial.createFromLong(fingerprintLong)
}
