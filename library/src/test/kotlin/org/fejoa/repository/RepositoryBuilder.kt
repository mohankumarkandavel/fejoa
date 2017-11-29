package org.fejoa.repository

import org.fejoa.chunkcontainer.ChunkContainerRef
import org.fejoa.chunkcontainer.Hash
import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.support.*


object RepositoryBuilder {

    private val CC_REF = 0

    internal val TAG_IV = 0
    internal val TAG_ENCDATA = 1

    fun getSimpleCommitCallback(): CommitCallback = object: CommitCallback {
        suspend override fun objectIndexRefToLog(objectIndexRef: ChunkContainerRef): String {
            val buffer = commitPointerToLog(objectIndexRef)
            return buffer.encodeBase64()
        }

        override suspend fun objectIndexRefFromLog(logEntry: String): ChunkContainerRef {
            val logEntryBytes = logEntry.decodeBase64()
            return commitPointerFromLog(logEntryBytes)
        }
    }

    suspend private fun commitPointerToLog(commitPointer: ChunkContainerRef): ByteArray {
        val buffer = ProtocolBufferLight()
        var outputStream = AsyncByteArrayOutStream()
        commitPointer.write(outputStream)
        buffer.put(CC_REF, outputStream.toByteArray())

        return buffer.toByteArray()
    }

    suspend private fun commitPointerFromLog(bytes: ByteArray): ChunkContainerRef {
        val buffer = ProtocolBufferLight(bytes)

        val dataBytes = buffer.getBytes(CC_REF) ?: throw IOException("Missing data part")
        val chunkContainerRef = ChunkContainerRef.read(ByteArrayInStream(dataBytes).toAsyncInputStream())

        return chunkContainerRef
    }
}
