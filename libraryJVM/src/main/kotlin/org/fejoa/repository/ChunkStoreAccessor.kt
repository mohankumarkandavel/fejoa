package org.fejoa.repository

import org.fejoa.chunkstore.ChunkStore
import org.fejoa.storage.*
import org.fejoa.support.Future


fun ChunkStore.Transaction.toChunkTransaction(): ChunkTransaction {
    val transaction = this
    return object: ChunkTransaction {
        override fun getChunk(boxHash: HashValue): Future<ByteArray> {
            return Future.completedFuture(transaction.getChunk(boxHash))
        }
        override fun putChunk(data: ByteArray): Future<PutResult<HashValue>> {
            return Future.completedFuture(transaction.put(data))
        }
        override fun hasChunk(boxHash: HashValue): Future<Boolean> {
            return Future.completedFuture(transaction.contains(boxHash))
        }
        override fun finishTransaction(): Future<Unit> {
            return Future.completedFuture(transaction.commit())
        }
        override fun cancel(): Future<Unit> {
            return Future.completedFuture(transaction.cancel())
        }
    }
}
