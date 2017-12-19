package org.fejoa.repository.sync

import org.fejoa.network.RemotePipe
import org.fejoa.storage.ChunkTransaction
import org.fejoa.storage.Config
import org.fejoa.storage.HashValue
import org.fejoa.support.*


object HasChunksHandler {
    suspend fun handleHasChunks(transaction: ChunkTransaction, remotePipe: RemotePipe) {
        val haveChunks: MutableList<HashValue> = ArrayList()
        val inStream = remotePipe.inStream
        val nChunks = inStream.readInt()
        for (i in 0 until nChunks) {
            val hashValue = Config.newBoxHash()
            inStream.readFully(hashValue.bytes)
            if (transaction.hasChunk(hashValue).await())
                haveChunks.add(hashValue)
        }

        val outputStream = remotePipe.outStream
        Request.writeResponseHeader(outputStream, Request.RequestType.HAS_CHUNKS, Request.ResultType.OK)
        outputStream.writeInt(haveChunks.size)
        for (hashValue in haveChunks)
            outputStream.write(hashValue.bytes)
    }
}
