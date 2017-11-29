package org.fejoa.storage

import org.fejoa.repository.BranchLog


interface StorageBackend {
    interface BranchBackend {
        fun getChunkStorage(): ChunkStorage
        fun getBranchLog(): BranchLog
    }

    /**
     * Open a ChunkStorage
     *
     * @param branch ChunkStorage branch name
     * @param namespace in which the branch exists
     */
    suspend fun open(namespace: String, branch: String): BranchBackend
    suspend fun create(namespace: String, branch: String): BranchBackend
    suspend fun exists(namespace: String, branch: String): Boolean
    suspend fun delete(namespace: String, branch: String)
    suspend fun deleteNamespace(namespace: String)
}
