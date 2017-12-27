package org.fejoa

import org.fejoa.storage.DBHashValue
import org.fejoa.storage.DBMap
import org.fejoa.storage.HashValue
import org.fejoa.storage.StorageDir


class BranchIndex(val storageDir: StorageDir) {
    class BranchMap(storageDir: StorageDir, path: String) : DBMap<String, DBHashValue>(storageDir, path) {
        suspend override fun list(): Collection<String> {
            return dir.listFiles(path)
        }

        override fun get(key: String): DBHashValue {
            return DBHashValue(this, key)
        }
    }

    private val branchMap = BranchMap(storageDir, "branches")

    suspend fun update(branch: String, logTip: HashValue) {
        val entry = branchMap.get(branch)
        entry.write(logTip)
    }

    suspend fun commit() {
        storageDir.commit()
    }
}