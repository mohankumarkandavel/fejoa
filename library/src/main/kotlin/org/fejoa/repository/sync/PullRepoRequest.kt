package org.fejoa.repository.sync

import org.fejoa.network.RemotePipe
import org.fejoa.repository.Repository
import org.fejoa.storage.*
import org.fejoa.support.*


class PullRepoRequest(private val requestRepo: Repository) {
    suspend fun pull(remotePipe: RemotePipe, branch: String) {
        val header = LogEntryRequest.getRemoteTip(remotePipe, branch) ?: return

        val outputStream = remotePipe.outStream
        Request.writeRequestHeader(outputStream, Request.RequestType.GET_ALL_CHUNKS)

        val inputStream = remotePipe.inStream
        Request.receiveHeader(inputStream, Request.RequestType.GET_ALL_CHUNKS)
        val transaction = requestRepo.getCurrentTransaction()
        val rawAccessor = transaction.getRawAccessor()
        val chunkCount = inputStream.readLong()
        for (i in 0 until chunkCount) {
            val hashValue = Config.newBoxHash()
            inputStream.readFully(hashValue.bytes)
            val size = inputStream.readInt()
            val buffer = ByteArray(size)
            inputStream.readFully(buffer)
            val result = rawAccessor.putChunk(buffer).await()
            if (result.key != hashValue)
                throw IOException("Hash miss match.")
        }
        transaction.finishTransaction()

        val log = requestRepo.log
        log.add(header)
    }
}
