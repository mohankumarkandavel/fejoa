package org.rabinfingerprint.fingerprint

import org.fejoa.support.BigInteger
import org.fejoa.support.valueOf

import org.rabinfingerprint.datastructures.CircularByteQueue
import org.rabinfingerprint.fingerprint.Fingerprint.WindowedFingerprint
import org.rabinfingerprint.polynomial.Polynomial

/**
 * This class implements Rabin's Fingerprinting scheme using a Polynomial class
 * which handles calculations in the finite field GF(2). This is slower than
 * [RabinFingerprintLong], but can support monic irreducible polynomials
 * of nearly any degree.
 *
 * <pre>
 * Given an n-bit message m_0, ..., m_n-1, we view it as a polynomial of degree
 * n-1 over the finite field GF(2).
 *
 * m(x) = m_0 + m_1 x + ... + m_n-1 x_n-1
 *
 * We then pick a random irreducible polynomial p(x) of degree k over GF(2), and
 * we define the fingerprint of m to be
 *
 * f(x) = m(x) mod p(x)
 *
 * which can be viewed as a polynomial of degree k-1 or as a k-bit number.
</pre> *
 *
 * Because we are operating in the space defined by mod p(x), we only ever need
 * to store a fingerprint of k bits. All new bits are shifted in and modded with
 * p(x), resulting in a new k-bit number.
 *
 * <pre>
 * This follows from the fact that given an n-bit message:
 *
 * m(x) = m_0 x_n-1 + .... + m_n-2 x + m_n-1;
 *
 * and its fingerprint
 *
 * f(x) = m(x) mod p(x)
 * = r_0 x_k-1 + ... + r_k-2 x + r_k-1
 *
 * appending one more bit simplifies to
 *
 * f({m(x)*x + m_n}) = {m(x)*x + m_n} mod p(x);
</pre> *
 *
 * This means that to add one bit, we need merely shift in that bit and re-mod
 * with p(x). Similarly, we can add entire bytes, words, etc, by shifting them
 * in and modding with p(x). This can become untennable and slow if we shift too
 * much at once, as the mod calculation is done with synthetic division and will
 * take on the order of {number of bits shifted in} to complete.
 *
 * A table lookup method is obviously possible, and that is exactly what we do
 * in [RabinFingerprintLong].
 *
 *
 * "Rabin Fingerprint"
 * http://en.wikipedia.org/wiki/Rabin_fingerprint
 *
 * Michael O. Rabin, "Fingerprinting by Random Polynomials" (1981)
 * http://www.xmailserver.org/rabin.pdf
 *
 * Andrei Z. Broder, "Some applications of Rabin's fingerprinting method" (1993)
 * http://citeseer.ist.psu.edu/broder93some.html
 *
 */
class RabinFingerprintPolynomial constructor(poly: Polynomial, private val bytesPerWindow: Int = 0) : AbstractFingerprint(poly), WindowedFingerprint<Polynomial> {

    private val byteShift: BigInteger
    private val windowShift: BigInteger

    private val byteWindow: CircularByteQueue

    override var fingerprint: Polynomial = Polynomial()
        private set(value: Polynomial) {
            field = value
        }

    init {
        this.byteShift = valueOf(8)
        this.windowShift = valueOf((bytesPerWindow * 8).toLong())
        this.byteWindow = CircularByteQueue(bytesPerWindow + 1)
    }

    /**
     * Shifts in byte b and mods with poly
     *
     * If we have passed overflowed our window, we pop a byte
     */
    override fun pushByte(b: Byte) {
        synchronized(this) {
            var f = fingerprint
            f = f.shiftLeft(byteShift)
            f = f.or(Polynomial.createFromLong(b.toLong() and 0xFFL))
            f = f.mod(poly)

            fingerprint = f

            if (bytesPerWindow > 0) {
                byteWindow.add(b)
                if (byteWindow.isFull) popByte()
            }
        }
    }

    /**
     * Removes the contribution of the first byte in the byte queue from the
     * fingerprint.
     *
     * Note that the shift necessary to calculate it's contribution can be
     * sizeable, and the mod calculation will be similarly slow. It is therefore
     * done with the Polynomial to support arbitrary sizes.
     *
     * Note that despite this massive shift, the fingerprint will still result
     * in a k-bit number at the end of the calculation.
     */
    override fun popByte() {
        synchronized(this) {
            val b = byteWindow.poll()
            var f = Polynomial.createFromLong(b.toLong() and 0xFFL)
            f = f.shiftLeft(windowShift)
            f = f.mod(poly)

            fingerprint = fingerprint.xor(f)
        }
    }

    override fun reset() {
        synchronized(this) {
            this.fingerprint = Polynomial()
            this.byteWindow.clear()
        }
    }
}
