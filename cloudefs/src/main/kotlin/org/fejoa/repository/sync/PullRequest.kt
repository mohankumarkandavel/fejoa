package org.fejoa.repository.sync

import org.fejoa.storage.Hash
import org.fejoa.network.RemotePipe
import org.fejoa.repository.*
import org.fejoa.repository.sync.Request.RequestType.GET_CHUNKS
import org.fejoa.storage.*
import org.fejoa.support.*


class PullRequest(private val requestRepo: Repository, private val commitSignature: CommitSignature?) {

    /**
     * returns the remote tip
     */
    suspend fun pull(remotePipe: RemotePipe, branch: String, mergeStrategy: MergeStrategy): Hash? {
        val remoteTipMessage = LogEntryRequest.getRemoteTip(remotePipe, branch)?.message ?: return null
        val remoteRepoRef = requestRepo.branchLogIO.readFromLog(remoteTipMessage)
        val transaction = requestRepo.getCurrentTransaction()

        // up to date?
        val localTip = requestRepo.getHead()
        if (localTip == remoteRepoRef.head)
            return remoteRepoRef.head

        val chunkFetcher = createRemotePipeFetcher(transaction, remotePipe)
        chunkFetcher.enqueueRepositoryJob(remoteRepoRef)
        chunkFetcher.fetch()

        val remoteRep = Repository.open(branch, remoteRepoRef, requestRepo.branchBackend, requestRepo.crypto)
        val merged = requestRepo.merge(listOf(remoteRep), mergeStrategy)
        val newHead = when (merged) {
            Database.MergeResult.MERGED -> requestRepo.commit("Merge after pull".toUTF(), commitSignature)
            Database.MergeResult.FAST_FORWARD -> { requestRepo.getHeadCommit()!!.getHash() }
        }

        return remoteRepoRef.head
    }


    companion object {

        fun createRemotePipeFetcher(transaction: ChunkAccessors.Transaction,
                                    remotePipe: RemotePipe): ChunkFetcher {
            return ChunkFetcher(transaction, object : ChunkFetcher.FetcherBackend {
                override suspend fun fetch(transaction: ChunkTransaction, requestedChunks: List<HashValue>) {
                    val outputStream = remotePipe.outStream
                    Request.writeRequestHeader(outputStream, GET_CHUNKS)
                    outputStream.writeLong(requestedChunks.size.toLong())
                    for (hashValue in requestedChunks)
                        outputStream.write(hashValue.bytes)

                    val inputStream = remotePipe.inStream
                    Request.receiveHeader(inputStream, GET_CHUNKS)
                    val chunkCount = inputStream.readLong()
                    if (chunkCount != requestedChunks.size.toLong()) {
                        throw IOException("Received chunk count is: " + chunkCount + " but " + requestedChunks.size
                                + " expected.")
                    }

                    for (i in 0 until chunkCount) {
                        val hashValue = Config.newBoxHash()
                        inputStream.readFully(hashValue.bytes)
                        val size = inputStream.readInt()

                        val buffer = ByteArray(size)
                        inputStream.readFully(buffer)
                        val result = transaction.putChunk(buffer).await()
                        if (result.key != hashValue)
                            throw IOException("Hash miss match. Expected:" + hashValue + ", Got: " + result.key)
                    }
                }
            })
        }
    }
}
