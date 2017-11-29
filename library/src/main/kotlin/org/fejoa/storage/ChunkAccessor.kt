package org.fejoa.storage

import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.CryptoInterface
import org.fejoa.crypto.SecretKey
import org.fejoa.support.*



interface StorageTransaction {
    fun finishTransaction(): Future<Unit>
    fun cancel(): Future<Unit>
}

interface ChunkTransaction : StorageTransaction {
    fun getChunk(boxHash: HashValue): Future<ByteArray>
    fun putChunk(data: ByteArray): Future<PutResult<HashValue>>
    fun hasChunk(boxHash: HashValue): Future<Boolean>
}

interface ChunkStorage {
    fun startTransaction(): ChunkTransaction
}


interface ChunkAccessor {
    fun getChunk(hash: ChunkRef): Future<ByteArray>

    fun putChunk(data: ByteArray, ivHash: HashValue): Future<PutResult<HashValue>>

    fun releaseChunk(data: HashValue): Future<Unit>
}

fun ChunkTransaction.toChunkAccessor(): ChunkAccessor {
    val that = this
    return object : ChunkAccessor {
        override fun getChunk(hash: ChunkRef): Future<ByteArray> {
            return that.getChunk(hash.boxHash)
        }

        override fun putChunk(data: ByteArray, ivHash: HashValue): Future<PutResult<HashValue>> {
            return that.putChunk(data)
        }

        override fun releaseChunk(data: HashValue): Future<Unit> {
            return Future.completedFuture(Unit)
        }
    }
}

fun ChunkAccessor.encrypted(crypto: CryptoInterface, secretKey: SecretKey, symmetric: CryptoSettings.Symmetric): ChunkAccessor {
    val that = this
    return object : ChunkAccessor {
        private fun getIv(hashValue: ByteArray): ByteArray {
            return hashValue.copyOfRange(0, symmetric.ivSize / 8)
        }

        override fun getChunk(pointer: ChunkRef): Future<ByteArray> = async {
            val encrypted = that.getChunk(pointer).await()
            return@async crypto.decryptSymmetric(encrypted, secretKey, getIv(pointer.iv), symmetric).await()
        }

        override fun putChunk(data: ByteArray, ivHash: HashValue): Future<PutResult<HashValue>> = async {
            val encrypted = crypto.encryptSymmetric(data, secretKey, getIv(ivHash.bytes), symmetric).await()
            return@async that.putChunk(encrypted, ivHash).await()
        }

        override fun releaseChunk(data: HashValue): Future<Unit> {
            return that.releaseChunk(data)
        }
    }
}

fun ChunkAccessor.compressed(): ChunkAccessor {
    val that = this
    return object : ChunkAccessor {
        override fun getChunk(pointer: ChunkRef): Future<ByteArray> = async {
            val compressedBytes = that.getChunk(pointer).await()
            val outStream = ByteArrayOutStream()
            val inflateStream = InflateOutStream(outStream)
            inflateStream.write(compressedBytes)
            inflateStream.close()
            return@async outStream.toByteArray()
        }

        override fun putChunk(data: ByteArray, ivHash: HashValue): Future<PutResult<HashValue>> = async {
            val outStream = ByteArrayOutStream()
            val deflateStream = DeflateOutStream(outStream)
            deflateStream.write(data)
            deflateStream.close()
            return@async that.putChunk(outStream.toByteArray(), ivHash).await()
        }

        override fun releaseChunk(data: HashValue): Future<Unit> {
            return that.releaseChunk(data)
        }
    }
}