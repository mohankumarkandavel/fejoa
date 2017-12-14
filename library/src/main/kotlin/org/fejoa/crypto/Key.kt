package org.fejoa.crypto

interface Key {
    val type: CryptoSettings.KEY_TYPE
}

interface PublicKey : Key
interface PrivateKey: Key
interface SecretKey: Key
