package org.fejoa.crypto


open class SymBaseCredentials(val secretKey: SecretKey, val symmetric: CryptoSettings.Symmetric)

class SymCredentials(secretKey: SecretKey, val iv: ByteArray, symmetric: CryptoSettings.Symmetric)
    : SymBaseCredentials(secretKey, symmetric)