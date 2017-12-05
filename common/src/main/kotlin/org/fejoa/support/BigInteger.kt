package org.fejoa.support

expect class BigInteger {
    constructor(value: String)
    constructor(value: String, radix: Int)

    open fun modPow(exp: BigInteger, mod: BigInteger): BigInteger
    open fun add(value: BigInteger): BigInteger
    open fun pow(value: Int): BigInteger
    open fun subtract(value: BigInteger): BigInteger
    open fun toString(radix: Int): String
    open fun shiftRight(n: Int): BigInteger
    open fun shiftLeft(n: Int): BigInteger
    open override fun equals(other: Any?): Boolean
    open fun compareTo(other: BigInteger): Int
    open fun or(value: BigInteger): BigInteger
    open fun and(value: BigInteger): BigInteger
    open fun negate(): BigInteger
    open fun setBit(bit: Int): BigInteger
    open fun testBit(bit: Int): Boolean

    open fun toLong(): Long
    open fun toInt(): Int
}
