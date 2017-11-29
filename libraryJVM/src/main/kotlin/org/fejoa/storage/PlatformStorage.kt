package org.fejoa.storage

import org.fejoa.chunkstore.ChunkStore
import org.fejoa.repository.BranchLog
import org.fejoa.repository.ChunkStoreBranchLog
import org.fejoa.repository.toChunkTransaction
import java.io.File


actual fun platformCreateStorage(): StorageBackend {
    return ChunkStoreBackend()
}

class ChunkStoreBackend : StorageBackend {
    class ChunkStoreBranchBackend(val chunkStore: ChunkStore, val namespace: String, val branch: String)
        : StorageBackend.BranchBackend {
        override fun getChunkStorage(): ChunkStorage {
            return object : ChunkStorage {
                override fun startTransaction(): ChunkTransaction {
                    return chunkStore.openTransaction().toChunkTransaction()
                }
            }
        }

        override fun getBranchLog(): BranchLog {
            return ChunkStoreBranchLog(File(namespace), branch)
        }
    }

    suspend override fun open(namespace: String, branch: String): StorageBackend.BranchBackend {
        return ChunkStoreBranchBackend(ChunkStore.open(File(namespace), branch), namespace, branch)
    }

    suspend override fun create(namespace: String, branch: String): StorageBackend.BranchBackend {
        val dir = File(namespace)
        dir.mkdirs()
        return ChunkStoreBranchBackend(ChunkStore.create(dir, branch), namespace, branch)
    }

    suspend override fun exists(namespace: String, branch: String): Boolean {
        return ChunkStore.exists(File(namespace), branch)
    }

    suspend override fun delete(namespace: String, branch: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    suspend override fun deleteNamespace(namespace: String) {
        File(namespace).deleteRecursively()
    }
}
