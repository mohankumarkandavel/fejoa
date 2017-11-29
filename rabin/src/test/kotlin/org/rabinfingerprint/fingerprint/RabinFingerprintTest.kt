package org.rabinfingerprint.fingerprint

import org.rabinfingerprint.polynomial.Polynomial
import org.fejoa.support.Random
import kotlin.test.Test
import kotlin.test.assertEquals


class RabinFingerprintTest {

    @Test
    fun testPolynomialsAndLongs() {
        // generate random data
        val data = ByteArray(1024)
        val random = Random()
        random.read(data)

        // generate random irreducible polynomial
        val p = Polynomial.createIrreducible(53)
        val rabin0 = RabinFingerprintPolynomial(p)
        val rabin1 = RabinFingerprintLong(p)
        rabin0.pushBytes(data)
        rabin1.pushBytes(data)
        assertEquals(0, rabin0.fingerprint.compareTo(rabin1.fingerprint))
    }

    @Test
    fun testWindowing() {
        doTestWindowing(true, 5)
        doTestWindowing(false, 5)
    }

    fun doTestWindowing(usePolynomials: Boolean, times: Int) {
        val random = Random()
        val windowSize = 8

        for (i in 0..times - 1) {
            // Generate Random Irreducible Polynomial
            val p = Polynomial.createIrreducible(53)

            val rabin0: Fingerprint<Polynomial>
            val rabin1: Fingerprint<Polynomial>
            if (usePolynomials) {
                rabin0 = RabinFingerprintPolynomial(p, windowSize)
                rabin1 = RabinFingerprintPolynomial(p)
            } else {
                rabin0 = RabinFingerprintLongWindowed(p, windowSize)
                rabin1 = RabinFingerprintLong(p)
            }

            // Generate Random Data
            val data = ByteArray(windowSize.toInt() * 5)
            random.read(data)

            // Read 3 windows of data to populate one fingerprint
            for (j in 0..windowSize * 3 - 1) {
                rabin0.pushByte(data[j.toInt()])
            }

            // Starting from same offset, continue fingerprinting for 1 more window
            for (j in windowSize * 3..windowSize * 4 - 1) {
                rabin0.pushByte(data[j.toInt()])
                rabin1.pushByte(data[j.toInt()])
            }

            assertEquals(0, rabin0.fingerprint.compareTo(rabin1.fingerprint))
        }
    }
}

