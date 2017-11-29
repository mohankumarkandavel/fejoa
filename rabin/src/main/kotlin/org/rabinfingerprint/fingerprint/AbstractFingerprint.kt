package org.rabinfingerprint.fingerprint

import org.rabinfingerprint.polynomial.Polynomial

abstract class AbstractFingerprint(protected val poly: Polynomial) : Fingerprint<Polynomial> {

    override fun pushBytes(bytes: ByteArray) {
        for (b in bytes) {
            pushByte(b)
        }
    }

    override fun pushBytes(bytes: ByteArray, offset: Int, length: Int) {
        val max = offset + length
        var i = offset
        while (i < max) {
            pushByte(bytes[i++])
        }
    }

    abstract override fun pushByte(b: Byte)
    abstract override fun reset()
    abstract override val fingerprint: Polynomial

    override fun toString(): String {
        return fingerprint.toHexString()
    }
}