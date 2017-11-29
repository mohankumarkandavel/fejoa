package org.fejoa.jsbindings

external open class BigInteger {
    fun modPow(exp: Any, mod: Any): BigInteger
    fun add(value: BigInteger): BigInteger
    fun add(value: Int): BigInteger
    fun pow(value: BigInteger): BigInteger
    fun pow(value: Int): BigInteger
    fun subtract(value: BigInteger): BigInteger
    fun subtract(value: Int): BigInteger
    fun toString(radix: Int): String
    fun shiftRight(n: Int): BigInteger
    fun shiftLeft(n: Int): BigInteger
    fun compare(value: BigInteger): Int
    fun compare(value: Int): Int
    fun or(value: BigInteger): BigInteger
    fun and(value: BigInteger): BigInteger
    fun negate(): BigInteger
}

external fun bigInt(number: BigInteger): BigInteger
external fun bigInt(number: Int): BigInteger
external fun bigInt(argument: String, radix: Int = definedExternally): BigInteger

