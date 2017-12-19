package org.fejoa.repository.sync

import org.fejoa.network.RemotePipe
import org.fejoa.storage.ChunkTransaction
import org.fejoa.storage.Config
import org.fejoa.storage.HashValue
import org.fejoa.support.*


object PushHandler {
    suspend fun handlePutChunks(transaction: ChunkTransaction, logGetter: RequestHandler.BranchLogGetter,
                                remotePipe: RemotePipe) {
        val inputStream = remotePipe.inStream
        val branch = StreamHelper.readString(inputStream, 64)
        val branchLog = logGetter.get(branch)
        if (branchLog == null) {
            RequestHandler.makeError(remotePipe.outStream, Request.RequestType.PUT_CHUNKS,
                    "No access to branch: " + branch)
            return
        }
        val expectedTip = Config.newBoxHash()
        inputStream.readFully(expectedTip.bytes)
        val entryId = Config.newBoxHash()
        inputStream.readFully(entryId.bytes)
        val logMessage = StreamHelper.readString(inputStream, LogEntryRequest.MAX_HEADER_SIZE)
        val nChunks = inputStream.readInt()
        val added: MutableList<HashValue> = ArrayList()
        for (i in 0 until nChunks) {
            val chunkHash = Config.newBoxHash()
            inputStream.readFully(chunkHash.bytes)
            val chunkSize = inputStream.readInt()
            val buffer = ByteArray(chunkSize)
            inputStream.readFully(buffer)
            val result = transaction.putChunk(buffer).await()
            if (result.key != chunkHash)
                throw IOException("Hash miss match.")
            added.add(chunkHash)
        }

        transaction.finishTransaction()
        val outputStream = remotePipe.outStream

        val latest = branchLog.getHead().await()
        if (latest != null && latest.entryId != expectedTip) {
            Request.writeResponseHeader(outputStream, Request.RequestType.PUT_CHUNKS, Request.ResultType.PULL_REQUIRED)
            return
        }
        branchLog.add(entryId, logMessage, added)

        Request.writeResponseHeader(outputStream, Request.RequestType.PUT_CHUNKS, Request.ResultType.OK)
    }
}
