package org.rabinfingerprint.polynomial

interface Arithmetic<T> {
    fun add(that: T): T
    fun subtract(that: T): T
    fun multiply(that: T): T
    fun and(that: T): T
    fun or(that: T): T
    fun xor(that: T): T
    operator fun mod(that: T): T
    fun gcd(that: T): T
}