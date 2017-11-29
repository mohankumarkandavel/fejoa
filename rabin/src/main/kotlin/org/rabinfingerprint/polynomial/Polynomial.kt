package org.rabinfingerprint.polynomial

import org.fejoa.support.BigInteger
import org.fejoa.support.BigIntegerCompanion
import org.fejoa.support.valueOf
import org.fejoa.support.Random


/**
 * An immutable polynomial in the finite field GF(2^k)
 *
 * Supports standard arithmetic in the field, as well as reducibility tests.
 */
class Polynomial : Arithmetic<Polynomial>, Comparable<Polynomial> {

    /** a reverse comparator so that polynomials are printed out correctly  */
    private class ReverseComparator : Comparator<BigInteger> {
        override fun compare(a: BigInteger, b: BigInteger): Int {
            return -1 * a.compareTo(b)
        }
    }

    /**
     * An enumeration representing the reducibility of the polynomial
     *
     * A polynomial p(x) in GF(2^k) is called irreducible over GF[2^k] if it is
     * non-constant and cannot be represented as the product of two or more
     * non-constant polynomials from GF(2^k).
     *
     * http://en.wikipedia.org/wiki/Irreducible_element
     */
    enum class Reducibility {
        REDUCIBLE, IRREDUCIBLE
    }

    /**
     * A (sorted) set of the degrees of the terms of the polynomial. The
     * sortedness helps quickly compute the degree as well as print out the
     * terms in order. The O(nlogn) performance of insertions and deletions
     * might actually hurt us, though, so we might consider moving to a HashSet
     */
    private val degrees: List<BigInteger>

    /**
     * Construct a new, empty polynomial
     */
    constructor() {
        this.degrees = createDegreesCollection()
    }

    /**
     * Construct a new polynomial copy of the input argument
     */
    constructor(p: Polynomial) : this(p.degrees) {}

    /**
     * Construct a new polynomial from a collection of degrees
     */
    protected constructor(degrees: List<BigInteger>) {
        this.degrees = degrees.sortedWith(ReverseComparator())
    }

    /**
     * Factory for create the copy of current degrees collection.
     */
    protected fun createDegreesCollectionCopy(): MutableList<BigInteger> {
        return ArrayList(degrees)
    }

    /**
     * Returns the degree of the highest term or -1 otherwise.
     */
    fun degree(): BigInteger {
        return if (degrees.isEmpty()) BigIntegerCompanion.ONE.negate() else degrees.first()
    }

    /**
     * Tests if the polynomial is empty, i.e. it has no terms
     */
    val isEmpty: Boolean
        get() = degrees.isEmpty()

    /**
     * Computes (this + that) in GF(2^k)
     */
    override fun add(that: Polynomial): Polynomial {
        return xor(that)
    }

    /**
     * Computes (this - that) in GF(2^k)
     */
    override fun subtract(that: Polynomial): Polynomial {
        return xor(that)
    }

    /**
     * Computes (this * that) in GF(2^k)
     */
    override fun multiply(that: Polynomial): Polynomial {
        val dgrs = createDegreesCollection()
        for (pa in this.degrees) {
            for (pb in that.degrees) {
                val sum = pa.add(pb)
                // xor the result
                if (dgrs.contains(sum))
                    dgrs.remove(sum)
                else
                    dgrs.add(sum)
            }
        }
        return Polynomial(dgrs)
    }

    /**
     * Computes (this & that) in GF(2^k)
     */
    override fun and(that: Polynomial): Polynomial {
        val dgrs = this.createDegreesCollectionCopy()
        dgrs.retainAll(that.degrees)
        return Polynomial(dgrs)
    }

    /**
     * Computes (this | that) in GF(2^k)
     */
    override fun or(that: Polynomial): Polynomial {
        val dgrs = this.createDegreesCollectionCopy()
        dgrs.addAll(that.degrees)
        return Polynomial(dgrs)
    }

    /**
     * Computes (this ^ that) in GF(2^k)
     */
    override fun xor(that: Polynomial): Polynomial {
        val dgrs0 = this.createDegreesCollectionCopy()
        dgrs0.removeAll(that.degrees)
        val dgrs1 = that.createDegreesCollectionCopy()
        dgrs1.removeAll(this.degrees)
        dgrs1.addAll(dgrs0)
        return Polynomial(dgrs1)
    }

    /**
     * Computes (this mod that) in GF(2^k) using synthetic division
     */
    override fun mod(that: Polynomial): Polynomial {
        val da = this.degree()
        val db = that.degree()
        var register = Polynomial(this.degrees)
        var i = da.subtract(db)
        while (i.compareTo(BigIntegerCompanion.ZERO) >= 0) {
            if (register.hasDegree(i.add(db))) {
                val shifted = that.shiftLeft(i)
                register = register.xor(shifted)
            }
            i = i.subtract(BigIntegerCompanion.ONE)
        }
        return register
    }

    /**
     * Computes (this << shift) in GF(2^k)
     */
    fun shiftLeft(shift: BigInteger): Polynomial {
        val dgrs = createDegreesCollection()
        for (degree in degrees) {
            val shifted = degree.add(shift)
            dgrs.add(shifted)
        }
        return Polynomial(dgrs)
    }

    /**
     * Computes (this >> shift) in GF(2^k)
     */
    fun shiftRight(shift: BigInteger): Polynomial {
        val dgrs = createDegreesCollection()
        for (degree in degrees) {
            val shifted = degree.subtract(shift)
            if (shifted.compareTo(BigIntegerCompanion.ZERO) < 0)
                continue
            dgrs.add(shifted)
        }
        return Polynomial(dgrs)
    }

    /**
     * Tests if there exists a term with degree k
     */
    fun hasDegree(k: BigInteger): Boolean {
        return degrees.contains(k)
    }

    /**
     * Sets the coefficient of the term with degree k to 1
     */
    fun setDegree(k: BigInteger): Polynomial {
        val dgrs = createDegreesCollection()
        dgrs.addAll(this.degrees)
        dgrs.add(k)
        return Polynomial(dgrs)
    }

    /**
     * Sets the coefficient of the term with degree k to 0
     */
    fun clearDegree(k: BigInteger): Polynomial {
        val dgrs = createDegreesCollection()
        dgrs.addAll(this.degrees)
        dgrs.remove(k)
        return Polynomial(dgrs)
    }

    /**
     * Toggles the coefficient of the term with degree k
     */
    fun toggleDegree(k: BigInteger): Polynomial {
        val dgrs = createDegreesCollection()
        dgrs.addAll(this.degrees)
        if (dgrs.contains(k)) {
            dgrs.remove(k)
        } else {
            dgrs.add(k)
        }
        return Polynomial(dgrs)
    }

    /**
     * Computes (this^e mod m).
     *
     * This algorithm requires at most this.degree() + m.degree() space.
     *
     * http://en.wikipedia.org/wiki/Modular_exponentiation
     */
    fun modPow(e: BigInteger, m: Polynomial): Polynomial {
        var eVar = e
        var result = Polynomial.ONE
        var b = Polynomial(this)
        while (eVar.compareTo(BigIntegerCompanion.ZERO) != 0) {
            if (eVar.testBit(0)) {
                result = result.multiply(b).mod(m)
            }
            eVar = eVar.shiftRight(1)
            b = b.multiply(b).mod(m)
        }
        return result
    }

    /**
     * Computes the greatest common divisor between polynomials using Euclid's
     * algorithm
     *
     * http://en.wikipedia.org/wiki/Euclids_algorithm
     */
    override fun gcd(that: Polynomial): Polynomial {
        var thatVar = that
        var a = Polynomial(this)
        while (!thatVar.isEmpty) {
            val t = Polynomial(thatVar)
            thatVar = a.mod(thatVar)
            a = t
        }
        return a
    }

    /**
     * Construct a BigInteger whose value represents this polynomial. This can
     * lose information if the degrees of the terms are larger than
     * Integer.MAX_VALUE;
     */
    fun toBigInteger(): BigInteger {
        var b = BigIntegerCompanion.ZERO
        for (degree in degrees) {
            b = b.setBit(degree.toLong().toInt())
        }
        return b
    }

    /**
     * Technically accurate but slow as hell.
     */
    fun toBigIntegerAccurate(): BigInteger {
        val b = BigIntegerCompanion.ZERO
        for (degree in degrees) {
            var term = BigIntegerCompanion.ONE
            var i = BigIntegerCompanion.ONE
            while (i.compareTo(degree) <= 0) {
                term = term.shiftLeft(1)
                i = i.add(BigIntegerCompanion.ONE)
            }
            b.add(term)
        }
        return b
    }

    /**
     * Returns a string of hex characters representing this polynomial
     */
    fun toHexString(): String {
        return toBigInteger().toString(16).toUpperCase()
    }

    /**
     * Returns a string of digits presenting this polynomial
     */
    fun toDecimalString(): String {
        return toBigInteger().toString()
    }

    /**
     * Returns a string of binary digits presenting this polynomial
     */
    fun toBinaryString(): String {
        var str = ""
        var deg = degree()
        while (deg.compareTo(BigIntegerCompanion.ZERO) >= 0) {
            if (degrees.contains(deg)) {
                str += "1"
            } else {
                str += "0"
            }
            deg = deg.subtract(BigIntegerCompanion.ONE)
        }
        return str
    }

    /**
     * Returns standard ascii representation of this polynomial in the form:
     *
     * e.g.: x^8 + x^4 + x^3 + x + 1
     */
    fun toPolynomialString(): String {
        var str = ""
        for (degree in degrees) {
            if (str.length != 0) {
                str += " + "
            }
            if (degree.compareTo(BigIntegerCompanion.ZERO) == 0) {
                str += "1"
            } else {
                str += ("x^" + degree)
            }
        }
        return str
    }

    /**
     * Default toString override uses the ascii representation
     */
    override fun toString(): String {
        return toPolynomialString()
    }

    /**
     * Tests the reducibility of the polynomial
     */
    fun isReducible(): Boolean {
        return getReducibility() == Reducibility.REDUCIBLE
    }

    /**
     * Tests the reducibility of the polynomial
     */
    // test trivial cases
    // do full-on reducibility test
    fun getReducibility() : Reducibility {
        if (this.compareTo(Polynomial.ONE) == 0)
            return Reducibility.REDUCIBLE
        return if (this.compareTo(Polynomial.X) == 0) Reducibility.REDUCIBLE else getReducibilityBenOr()
    }

    /**
     * BenOr Reducibility Test
     *
     * Tests and Constructions of Irreducible Polynomials over Finite Fields
     * (1997) Shuhong Gao, Daniel Panario
     *
     * http://citeseer.ist.psu.edu/cache/papers/cs/27167/http:zSzzSzwww.math.clemson.eduzSzfacultyzSzGaozSzpaperszSzGP97a.pdf/gao97tests.pdf
     */
    protected fun getReducibilityBenOr(): Reducibility {
        val degree = this.degree().toLong()
        for (i in 1..(degree / 2).toInt()) {
            val b = reduceExponent(i)
            val g = this.gcd(b)
            if (g.compareTo(Polynomial.ONE) != 0)
                return Reducibility.REDUCIBLE
        }

        return Reducibility.IRREDUCIBLE
    }

    /**
     * Rabin's Reducibility Test
     *
     * This requires the distinct prime factors of the degree, so we don't use
     * it. But this could be faster for prime degree polynomials
     */
    protected fun getReducibilityRabin(factors: IntArray): Reducibility {
        val degree = degree().toLong().toInt()
        for (i in factors.indices) {
            val n_i = factors[i]
            val b = reduceExponent(n_i)
            val g = this.gcd(b)
            if (g.compareTo(Polynomial.ONE) != 0)
                return Reducibility.REDUCIBLE
        }

        val g = reduceExponent(degree)
        return if (!g.isEmpty) Reducibility.REDUCIBLE else Reducibility.IRREDUCIBLE

    }

    /**
     * Computes ( x^(2^p) - x ) mod f
     *
     * This function is useful for computing the reducibility of the polynomial
     */
    private fun reduceExponent(p: Int): Polynomial {
        // compute (x^q^p mod f)
        val q_to_p = Q.pow(p)
        val x_to_q_to_p = X.modPow(q_to_p, this)

        // subtract (x mod f)
        return x_to_q_to_p.xor(X).mod(this)
    }

    /**
     * Compares this polynomial to the other
     */
    override fun compareTo(other: Polynomial): Int {
        val cmp = degree().compareTo(other.degree())
        if (cmp != 0) return cmp
        // get first degree difference
        val x = this.xor(other)
        if (x.isEmpty) return 0
        return if (this.hasDegree(x.degree())) 1 else -1
    }

    companion object {

        /** number of elements in the finite field GF(2^k)  */
        val Q = valueOf(2)

        /** the polynomial "x"  */
        val X = Polynomial.createFromLong(2)

        /** the polynomial "1"  */
        val ONE = Polynomial.createFromLong(1)

        /**
         * Constructs a polynomial using the bits from a long. Note that Java does
         * not support unsigned longs.
         */
        fun createFromLong(l: Long): Polynomial {
            val dgrs = createDegreesCollection()
            for (i in 0..63) {
                if ((l shr i) and 1 == 1L)
                    dgrs.add(valueOf(i.toLong()))
            }
            return Polynomial(dgrs)
        }

        fun createFromBytes(bytes: ByteArray): Polynomial {
            val dgrs = createDegreesCollection()
            var degree = 0
            for (i in bytes.indices.reversed()) {
                for (j in 0..7) {
                    if (bytes[i].toInt() shr j and 1 == 1) {
                        dgrs.add(valueOf(degree.toLong()))
                    }
                    degree++
                }
            }
            return Polynomial(dgrs)
        }

        /**
         * Constructs a polynomial using the bits from an array of bytes, limiting
         * the degree to the specified size.
         *
         * We set the final degree to ensure a monic polynomial of the correct
         * degree.
         */
        fun createFromBytes(bytes: ByteArray, degree: Long): Polynomial {
            val dgrs = createDegreesCollection()
            for (i in 0..degree - 1) {
                if (Polynomials.getBit(bytes, i.toInt()))
                    dgrs.add(valueOf(i))
            }
            dgrs.add(valueOf(degree))
            return Polynomial(dgrs)
        }

        /**
         * Constructs a random polynomial of degree "degree"
         */
        fun createRandom(degree: Int): Polynomial {
            val random = Random()
            val bytes = ByteArray(degree / 8 + 1)
            random.read(bytes)
            return createFromBytes(bytes, degree.toLong())
        }

        /**
         * Finds a random irreducible polynomial of degree "degree"
         */
        fun createIrreducible(degree: Int): Polynomial {
            while (true) {
                val p = createRandom(degree)
                if (p.getReducibility() == Reducibility.IRREDUCIBLE)
                    return p
            }
        }

        /**
         * Factory for create the degrees collection.
         */
        protected fun createDegreesCollection(): MutableList<BigInteger> {
            return ArrayList()
        }
    }
}
