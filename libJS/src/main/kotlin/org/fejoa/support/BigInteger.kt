package org.fejoa.support

import org.fejoa.jsbindings.bigInt

actual class BigInteger private constructor(private val internalInt: org.fejoa.jsbindings.BigInteger) {
    actual constructor(value: String): this(bigInt(value))
    actual constructor(value: String, radix: Int): this(bigInt(value, radix))

    constructor(argument: Long): this(bigInt(argument.toInt()))

    companion object {
        val ZERO = BigInteger(0)
        val ONE = BigInteger(1)

        fun valueOf(value: Long): BigInteger {
            return BigInteger(value)
        }
    }

    actual open fun modPow(exp: BigInteger, mod: BigInteger): BigInteger {
        return BigInteger(internalInt.modPow(exp, mod))
    }
    actual open fun add(value: BigInteger): BigInteger {
        return BigInteger(internalInt.add(value.internalInt))
    }
    fun add(value: Int): BigInteger {
        return BigInteger(internalInt.add(value))
    }
    fun pow(value: BigInteger): BigInteger {
        return BigInteger(internalInt.pow(value.internalInt))
    }
    actual open fun pow(value: Int): BigInteger {
        return BigInteger(internalInt.pow(value))
    }
    actual open fun subtract(value: BigInteger): BigInteger {
        return BigInteger(internalInt.subtract(value.internalInt))
    }
    fun subtract(value: Int): BigInteger {
        return BigInteger(internalInt.subtract(value))
    }
    actual open fun toString(radix: Int): String {
        return internalInt.toString(radix)
    }
    actual open fun shiftRight(n: Int): BigInteger {
        return BigInteger(internalInt.shiftRight(n))
    }
    actual open fun shiftLeft(n: Int): BigInteger {
        return BigInteger(internalInt.shiftLeft(n))
    }
    actual override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        if (other !is BigInteger)
            return false
        return compareTo(other) == 0
    }
    actual open fun compareTo(other: BigInteger): Int {
        return internalInt.compare(other.internalInt)
    }
    fun compareTo(value: Int): Int {
        return internalInt.compare(value)
    }
    actual open fun or(value: BigInteger): BigInteger {
        return BigInteger(internalInt.or(value.internalInt))
    }
    actual open fun and(value: BigInteger): BigInteger {
        return BigInteger(internalInt.and(value.internalInt))
    }
    actual open fun negate(): BigInteger {
        return BigInteger(internalInt.negate())
    }

    actual open fun setBit(bit: Int): BigInteger {
        if (bit < 0)
            throw Exception("Invalid bit number $bit")
        var orValue = ONE
        if (bit > 0)
            orValue = ONE.shiftLeft(bit)
        return or(orValue)
    }

    actual open fun testBit(bit: Int): Boolean {
        if (bit < 0)
            throw Exception("Invalid bit number $bit")
        var andValue = ONE
        if (bit > 0)
            andValue = ONE.shiftLeft(bit)
        return and(andValue).compareTo(0) != 0
    }

    actual open fun toLong(): Long {
        return toString(10).toLong()
    }

    fun longValue(): Long {
        return toLong()
    }

    actual open fun toInt(): Int {
        val string = toString(10)
        return string.toInt(10)
    }
}
