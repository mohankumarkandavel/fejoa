package org.fejoa.crypto

actual fun platformCrypto(): CryptoInterface {
    return SubtleCrypto()
}