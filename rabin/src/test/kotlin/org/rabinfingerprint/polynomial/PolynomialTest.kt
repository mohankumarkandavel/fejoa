package org.rabinfingerprint.polynomial

import kotlin.test.Test
import kotlin.test.*
import org.rabinfingerprint.polynomial.Polynomial.Reducibility
import kotlin.math.abs


class PolynomialTest {
    /**
     * Tests loading and printing out of polynomials.
     *
     * The polys used are from here:
     * http://en.wikipedia.org/wiki/Finite_field_arithmetic#Rijndael.27s_finite_field
     */
    @Test
    fun testPolynomialArithmetic() {
        val pa = Polynomial.createFromLong(0x53)
        val pb = Polynomial.createFromLong(0xCA)
        val pm = Polynomial.createFromLong(0x11B)
        val px = pa.multiply(pb)

        assertEquals(0x3F7E, px.toBigInteger().toLong())
        val pabm = px.mod(pm)
        assertEquals(0x1, pabm.toBigInteger().toLong())
    }

    /**
     * According to Rabin, the expected number of tests required to find an
     * irreducible polynomial from a randomly chosen monic polynomial of degree
     * k is k (neat, huh!).
     *
     * Therefore, we should see an average spread of k reducible polynomials
     * between irreducible ones. This test computes the running average of these
     * spreads for verification.
     *
     * This is not a perfect correctness verification, but it is a good "mine
     * canary".
     */

    @Test
    fun testIrreducibleSpread() {
        val degree = 15
        val stats = getSpread(degree, 200)
        val spread = abs(stats.average() - degree)
        assertTrue { spread < 3 }
        //assertTrue("Spread of irreducible polynomials is out of expected range: " + spread, spread < 3)
    }

    fun getSpread(degree: Int, tests: Int): Stats {
        var testsVar = tests
        var i = 0
        var last_i = 0
        val stats = Stats()
        while (testsVar > 0) {
            val f = Polynomial.createRandom(degree)
            val r = f.getReducibility()
            if (r === Reducibility.IRREDUCIBLE) {
                val spread = i - last_i
                stats.add(spread.toDouble())
                last_i = i
                testsVar--
            }
            i++
        }
        return stats
    }

}
