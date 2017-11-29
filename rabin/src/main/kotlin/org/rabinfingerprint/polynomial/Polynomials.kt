package org.rabinfingerprint.polynomial

object Polynomials {
    val DEFAULT_POLYNOMIAL_LONG = 0x375AD14A67FC7BL

    /**
     * Generates a handful of irreducible polynomials of the specified degree.
     */
    fun printIrreducibles(degree: Int) {
        for (i in 0..9) {
            val p = Polynomial.createIrreducible(degree)
            println(p.toPolynomialString())
        }
    }

    /**
     * Generates a large irreducible polynomial and prints out its
     * representation in ascii and hex.
     */
    fun printLargeIrreducible() {
        val p = Polynomial.createIrreducible(127)
        println(p.toPolynomialString())
        println(p.toHexString())
    }

    /**
     * Computes (a mod b) using synthetic division where a and b represent
     * polynomials in GF(2^k).
     */
    fun mod(a: Long, b: Long): Long {
        var aVar = a
        val ma = getMaxBit(aVar)
        val mb = getMaxBit(b)
        for (i in ma - mb downTo 0) {
            if (getBit(aVar, i + mb)) {
                val shifted = b shl i
                aVar = aVar xor shifted
            }
        }
        return aVar
    }

    /**
     * Returns the index of the maximum set bit. If no bits are set, returns -1.
     */
    fun getMaxBit(l: Long): Int {
        for (i in 64 - 1 downTo 0) {
            if (getBit(l, i))
                return i
        }
        return -1
    }

    /**
     * Returns the value of the bit at index of the long. The right most bit is
     * at index 0.
     */
    fun getBit(l: Long, index: Int): Boolean {
        return l shr index and 1L == 1L
    }

    /**
     * Returns the value of the bit at index of the byte. The right most bit is
     * at index 0.
     */
    fun getBit(b: Byte, index: Int): Boolean {
        return b.toInt() shr index and 1 == 1
    }

    /**
     * Returns the value of the bit at index of the byte. The right most bit is
     * at index 0 of the last byte in the array.
     */
    fun getBit(bytes: ByteArray, index: Int): Boolean {
        // byte array index
        val aidx = bytes.size - 1 - index / 8
        // bit index
        val bidx = index % 8
        // byte
        val b = bytes[aidx]
        // bit
        return getBit(b, bidx)
    }

}
