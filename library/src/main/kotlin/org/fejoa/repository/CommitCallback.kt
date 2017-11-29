package org.fejoa.repository

import org.fejoa.storage.HashValue
import org.fejoa.chunkcontainer.ChunkContainerRef


interface CommitCallback {
    /**
     * @return an identifier to be publicly stored in the, i.e. in the BranchLog
     */
    fun logHash(objectIndexRef: ChunkContainerRef): HashValue {
        return objectIndexRef.boxHash
    }

    /**
     * Serializes the latest object index chunk container ref.
     *
     * Here is also the place to encrypt/decrypt the objectIndexRef.
     */
    suspend fun objectIndexRefToLog(objectIndexRef: ChunkContainerRef): String
    suspend fun objectIndexRefFromLog(logEntry: String): ChunkContainerRef
}
