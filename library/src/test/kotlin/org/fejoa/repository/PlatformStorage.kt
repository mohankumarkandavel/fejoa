package org.fejoa.repository

import org.fejoa.storage.ChunkStorage

expect fun platformCreateTestStorage(dir: String, name: String): TestStorage


interface TestStorage {
    fun cleanUp()
    fun getChunkStorage(): ChunkStorage
    fun getBranchLog(): BranchLog
}



