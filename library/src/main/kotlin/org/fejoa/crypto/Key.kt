package org.fejoa.crypto

interface Key {
    val algorithm: String
    fun toByteArray(): ByteArray
}

interface PublicKey : Key
interface PrivateKey: Key
interface SecretKey: Key