package org.fejoa.support

actual fun valueOf(value: Long): BigInteger {
    return java.math.BigInteger.valueOf(value)
}

actual typealias BigInteger = java.math.BigInteger