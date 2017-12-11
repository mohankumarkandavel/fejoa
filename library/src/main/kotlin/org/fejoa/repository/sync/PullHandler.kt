package org.fejoa.repository.sync

import org.fejoa.network.RemotePipe
import org.fejoa.storage.ChunkTransaction
import org.fejoa.storage.Config
import org.fejoa.storage.HashValue
import org.fejoa.support.*


object PullHandler {
    suspend fun handleGetChunks(chunkStore: ChunkTransaction, remotePipe: RemotePipe) {
        val nRequestedChunks = remotePipe.inStream.readLong()
        val requestedChunks: MutableList<HashValue> = ArrayList()
        for (i in 0 until nRequestedChunks) {
            val hashValue = Config.newBoxHash()
            remotePipe.inStream.readFully(hashValue.bytes)
            requestedChunks.add(hashValue)
        }


        val outputStream = remotePipe.outStream
        Request.writeResponseHeader(outputStream, Request.RequestType.GET_CHUNKS, Request.ResultType.OK)

        outputStream.writeLong(requestedChunks.size.toLong())

        for (hashValue in requestedChunks) {
            val chunk = chunkStore.getChunk(hashValue).await()
            //TODO: Return error if chunk is not found
            outputStream.write(hashValue.bytes)
            outputStream.writeInt(chunk.size)
            outputStream.write(chunk)
        }
    }

    suspend fun handleGetAllChunks(chunkStore: ChunkTransaction, receiver: RemotePipe) {
        TODO("Implement ChunkTransaction iterator")

        /*val outputStream = it.outStream
        Request.writeResponseHeader(outputStream, Request.RequestType.GET_ALL_CHUNKS, Request.ResultType.OK)

        outputStream.writeLong(chunkStore.size())
        val iterator = chunkStore.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            outputStream.write(entry.key.getBytes())
            outputStream.writeInt(entry.data.length)
            outputStream.write(entry.data)
        }
        iterator.unlock()*/
    }
}
