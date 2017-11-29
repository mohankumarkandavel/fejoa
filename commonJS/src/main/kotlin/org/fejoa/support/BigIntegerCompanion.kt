package org.fejoa.support


actual fun valueOf(value: Long): BigInteger {
    return BigInteger(value)
}