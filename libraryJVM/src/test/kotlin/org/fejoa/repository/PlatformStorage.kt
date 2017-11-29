package org.fejoa.repository

import org.fejoa.chunkstore.ChunkStore
import org.fejoa.storage.ChunkStorage
import org.fejoa.storage.ChunkTransaction
import java.io.File


actual fun platformCreateTestStorage(dir: String, name: String): TestStorage {
    return ChunkStoreTestStorage(dir, name)
}

class ChunkStoreTestStorage(dir: String, val name: String) : TestStorage {
    val dirFile = File(dir)
    val chunkStore: ChunkStore

    init {
        dirFile.mkdirs()

        chunkStore = if (ChunkStore.exists(dirFile, name))
            ChunkStore.open(dirFile, name)
        else
            ChunkStore.create(dirFile, name)
    }

    override fun cleanUp() {
        dirFile.deleteRecursively()
    }

    override fun getChunkStorage(): ChunkStorage {
        return object : ChunkStorage {
            override fun startTransaction(): ChunkTransaction {
                return chunkStore.openTransaction().toChunkTransaction()
            }
        }
    }

    override fun getBranchLog(): BranchLog {
        return ChunkStoreBranchLog(dirFile, name)
    }
}

