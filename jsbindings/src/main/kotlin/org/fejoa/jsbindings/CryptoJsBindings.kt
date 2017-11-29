package org.fejoa.jsbindings

import org.w3c.dom.Window
import org.w3c.dom.get
import kotlin.js.Json
import kotlin.js.Promise


external interface CryptoKey

external interface SubtleCrypto {
    fun digest(algo: String, buffer: ByteArray): Promise<ByteArray>
    fun generateKey(algo: Json, extractable: Boolean, keyUsages: Array<String>): Promise<CryptoKey>

    fun deriveKey(algo: Json, masterKey: CryptoKey, derivedKeyAlgo: Json, extractable: Boolean,
                  keyUsages: Array<String>): Promise<CryptoKey>

    fun encrypt(algorithm: Json, key: CryptoKey, data: ByteArray): Promise<ByteArray>
    fun decrypt(algorithm: Json, key: CryptoKey, data: ByteArray): Promise<ByteArray>

    fun importKey(format: String, keyData: ByteArray, algo: Json, extractable: Boolean,
                  usages: Array<String>): Promise<CryptoKey>
    fun exportKey(format: String, key: CryptoKey): Promise<Any>
}

external interface Crypto {
    val subtle: SubtleCrypto
    fun getRandomValues(typedArray: Any)
}

fun Window.crypto(): Crypto {
    return window["crypto"].unsafeCast<Crypto>()
}
