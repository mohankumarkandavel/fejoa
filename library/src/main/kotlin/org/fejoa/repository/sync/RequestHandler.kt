package org.fejoa.repository.sync

import kotlinx.serialization.json.JSON
import org.fejoa.network.RemotePipe
import org.fejoa.repository.BranchLog
import org.fejoa.repository.BranchLogEntry
import org.fejoa.storage.ChunkTransaction
import org.fejoa.support.*


class RequestHandler(private val chunkStore: ChunkTransaction, private val logGetter: BranchLogGetter) {
    enum class Result(val value: Int, val description: String) {
        OK(0, "ok"),
        MISSING_ACCESS_RIGHTS(1, "missing access rights"),
        ERROR(-1, "error")
    }

    interface BranchLogGetter {
        operator fun get(branch: String): BranchLog?
    }

    private fun checkAccessRights(request: Request.RequestType, accessRights: AccessRight): Boolean {
        when (request) {
            Request.RequestType.GET_REMOTE_TIP,
            Request.RequestType.GET_CHUNKS -> if (accessRights.value and AccessRight.PULL.value == 0)
                return false
            Request.RequestType.PUT_CHUNKS,
            Request.RequestType.HAS_CHUNKS -> if (accessRights.value and AccessRight.PUSH.value == 0)
                return false
            Request.RequestType.GET_ALL_CHUNKS -> if (accessRights.value and AccessRight.PULL_CHUNK_STORE.value == 0)
                return false
        }
        return true
    }

    suspend fun handle(remotePipe: RemotePipe, accessRights: AccessRight): Result {
        try {
            val inputStream = remotePipe.inStream
            val requestValue = Request.receiveRequest(inputStream)
            val request = Request.RequestType.values().firstOrNull { it.value == requestValue }
                    ?: run {
                makeError(remotePipe.outStream, -1, "Unknown request: " + requestValue)
                return Result.ERROR
            }
            if (!checkAccessRights(request, accessRights))
                return Result.MISSING_ACCESS_RIGHTS
            val dummy = when (request) {
                Request.RequestType.GET_REMOTE_TIP -> handleGetRemoteTip(remotePipe)
                Request.RequestType.GET_CHUNKS -> PullHandler.handleGetChunks(chunkStore, remotePipe)
                Request.RequestType.PUT_CHUNKS -> PushHandler.handlePutChunks(chunkStore, logGetter, remotePipe)
                Request.RequestType.HAS_CHUNKS -> HasChunksHandler.handleHasChunks(chunkStore, remotePipe)
                Request.RequestType.GET_ALL_CHUNKS -> PullHandler.handleGetAllChunks(chunkStore, remotePipe)
            }
        } catch (e: IOException) {
            try {
                makeError(remotePipe.outStream, -1, "Internal error.")
            } catch (e1: IOException) {
                e1.printStackTrace()
            }

            return Result.ERROR
        }

        return Result.OK
    }

    suspend private fun handleGetRemoteTip(remotePipe: RemotePipe) {
        val branch = StreamHelper.readString(remotePipe.inStream, 64)

        val outputStream = remotePipe.outStream
        val localBranchLog = logGetter[branch] ?: run {
            makeError(outputStream, Request.RequestType.GET_REMOTE_TIP, "No access to branch: " + branch)
            return
        }

        Request.writeResponseHeader(outputStream, Request.RequestType.GET_REMOTE_TIP, Request.ResultType.OK)
        val head = localBranchLog.getHead().await() ?: BranchLogEntry()
        StreamHelper.writeString(outputStream, JSON.stringify(head))
    }

    companion object {
        suspend fun makeError(outputStream: AsyncOutStream, request: Request.RequestType, message: String) {
            makeError(outputStream, request.value, message)
        }

        suspend fun makeError(outputStream: AsyncOutStream, request: Int, message: String) {
            Request.writeResponseHeader(outputStream, request, Request.ResultType.ERROR)
            StreamHelper.writeString(outputStream, message)
        }
    }
}
