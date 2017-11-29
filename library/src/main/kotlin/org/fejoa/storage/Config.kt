package org.fejoa.storage


object Config {
    val SHA1_SIZE: Int = 20
    val SHA256_SIZE: Int = 32

    fun newSha1Hash(): HashValue {
        return HashValue(SHA1_SIZE)
    }

    val BOX_HASH_SIZE = SHA256_SIZE
    fun newBoxHash(): HashValue {
        return HashValue(BOX_HASH_SIZE)
    }

    val DATA_HASH_SIZE = SHA256_SIZE
    fun newDataHash(): HashValue {
        return HashValue(DATA_HASH_SIZE)
    }

    val IV_SIZE: Int = 16
    fun newIV(): ByteArray {
        return ByteArray(IV_SIZE)
    }
}
