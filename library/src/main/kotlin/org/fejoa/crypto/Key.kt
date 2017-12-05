package org.fejoa.crypto

interface Key {
    val algorithm: String
}

interface PublicKey : Key
interface PrivateKey: Key
interface SecretKey: Key

class SymCredentials(val secretKey: SecretKey, val symmetric: CryptoSettings.Symmetric)
