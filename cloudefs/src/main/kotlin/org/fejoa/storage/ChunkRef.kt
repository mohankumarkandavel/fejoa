package org.fejoa.storage

import org.fejoa.support.assert


open class ChunkRef(val dataHash: HashValue = Config.newDataHash(),
                    val boxHash: HashValue = Config.newBoxHash(),
                    open val iv: ByteArray = ByteArray(Config.IV_SIZE),
                    open var dataLength: Long = 0L) {
    companion object {
        private fun getIv(hashValue: ByteArray): ByteArray {
            return hashValue.copyOfRange(0, Config.IV_SIZE)
        }
    }

    init {
        assert(dataHash.size() == Config.DATA_HASH_SIZE && boxHash.size() == Config.BOX_HASH_SIZE
                && iv.size == Config.IV_SIZE)
    }

    constructor(dataHash: HashValue, boxHash: HashValue, iv: HashValue, dataLength: Long = 0L)
            : this(dataHash, boxHash, getIv(iv.bytes), dataLength)

    private fun copy(source: ByteArray, target: ByteArray) {
        if (source.size != target.size)
            throw Exception("Size mismatch")

        for (i in 0 until source.size)
            target[i] = source[i]
    }

    fun setTo(dataHash: HashValue = Config.newDataHash(),
              boxHash: HashValue = Config.newBoxHash(),
              iv: ByteArray = ByteArray(Config.IV_SIZE),
              dataLength: Long = 0L) {
        copy(dataHash.bytes, this.dataHash.bytes)
        copy(boxHash.bytes, this.boxHash.bytes)
        copy(iv, this.iv)
        this.dataLength = dataLength
    }

    fun setTo(dataHash: HashValue = Config.newDataHash(),
              boxHash: HashValue = Config.newBoxHash(),
              iv: HashValue,
              dataLength: Long = 0L) {
        setTo(dataHash, boxHash, iv.bytes.copyOfRange(0, Config.IV_SIZE), dataLength)
    }
}
