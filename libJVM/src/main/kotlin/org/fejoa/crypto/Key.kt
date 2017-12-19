package org.fejoa.crypto


open class KeyJVM(val key: java.security.Key, type: CryptoSettings.KEY_TYPE) : Key {
    override val type: CryptoSettings.KEY_TYPE = type

    fun toByteArray(): ByteArray {
        return key.encoded
    }
}

class PublicKeyJVM(key: java.security.PublicKey, type: CryptoSettings.KEY_TYPE) : PublicKey, KeyJVM(key, type)
class PrivateKeyJVM(key: java.security.PrivateKey, type: CryptoSettings.KEY_TYPE) : PrivateKey, KeyJVM(key, type)
class SecreteKeyJVM(key: javax.crypto.SecretKey, type: CryptoSettings.KEY_TYPE) : SecretKey, KeyJVM(key, type)