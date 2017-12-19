package org.fejoa.repository.sync

import org.fejoa.network.RemotePipe

import org.fejoa.repository.sync.Request.RequestType.HAS_CHUNKS
import org.fejoa.storage.*
import org.fejoa.support.readFully
import org.fejoa.support.readInt
import org.fejoa.support.writeInt


object HasChunksRequest {
    suspend fun hasChunks(remotePipe: RemotePipe, chunks: List<HashValue>): List<HashValue> {
        val outputStream = remotePipe.outStream
        Request
        Request.writeRequestHeader(outputStream, HAS_CHUNKS)

        outputStream.writeInt(chunks.size)
        for (hashValue in chunks)
            outputStream.write(hashValue.bytes)

        // reply
        val inputStream = remotePipe.inStream
        Request.receiveHeader(inputStream, HAS_CHUNKS)

        val nChunks = inputStream.readInt()
        val hasChunks = ArrayList<HashValue>()
        for (i in 0 until nChunks) {
            val hasChunk = Config.newBoxHash()
            inputStream.readFully(hasChunk.bytes)
            hasChunks.add(hasChunk)
        }
        return hasChunks
    }
}
