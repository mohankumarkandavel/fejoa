package org.fejoa.storage

import org.fejoa.chunkstore.ChunkStore
import org.fejoa.repository.BranchLog
import org.fejoa.repository.ChunkStoreBranchLog
import org.fejoa.repository.toChunkTransaction
import org.fejoa.support.PathUtils
import java.io.File


actual fun platformCreateStorage(context: String): StorageBackend {
    return ChunkStoreBackend(context)
}

class ChunkStoreBackend(val baseDir: String) : StorageBackend {
    class ChunkStoreBranchBackend(val chunkStore: ChunkStore, val baseDir: String, val namespace: String, val branch: String)
        : StorageBackend.BranchBackend {
        override fun getChunkStorage(): ChunkStorage {
            return object : ChunkStorage {
                override fun startTransaction(): ChunkTransaction {
                    return chunkStore.openTransaction().toChunkTransaction()
                }
            }
        }

        override fun getBranchLog(): BranchLog {
            return ChunkStoreBranchLog(File(PathUtils.appendDir(baseDir, namespace)), branch)
        }
    }

    private fun getFile(namespace: String): File {
        return File(PathUtils.appendDir(baseDir, namespace))
    }

    suspend override fun open(namespace: String, branch: String): StorageBackend.BranchBackend {
        return ChunkStoreBranchBackend(ChunkStore.open(getFile(namespace), branch), baseDir, namespace, branch)
    }

    suspend override fun create(namespace: String, branch: String): StorageBackend.BranchBackend {
        val dir = getFile(namespace)
        dir.mkdirs()
        return ChunkStoreBranchBackend(ChunkStore.create(dir, branch), baseDir, namespace, branch)
    }

    suspend override fun exists(namespace: String, branch: String): Boolean {
        return ChunkStore.exists(getFile(namespace), branch)
    }

    suspend override fun delete(namespace: String, branch: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    suspend override fun deleteNamespace(namespace: String) {
        getFile(namespace).deleteRecursively()
    }
}
