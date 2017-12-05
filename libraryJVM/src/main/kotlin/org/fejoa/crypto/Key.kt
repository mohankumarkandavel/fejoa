package org.fejoa.crypto


open class KeyJVM(val key: java.security.Key) : Key {
    override val algorithm: String
        get() = key.algorithm

    fun toByteArray(): ByteArray {
        return key.encoded
    }
}

class PublicKeyJVM(key: java.security.PublicKey) : PublicKey, KeyJVM(key)
class PrivateKeyJVM(key: java.security.PrivateKey) : PrivateKey, KeyJVM(key)
class SecreteKeyJVM(key: javax.crypto.SecretKey) : SecretKey, KeyJVM(key)