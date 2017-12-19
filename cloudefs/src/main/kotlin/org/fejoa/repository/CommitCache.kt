package org.fejoa.repository

import org.fejoa.storage.Hash


class CommitCache(private val repository: Repository) {
    private val commitCache = HashMap<Hash, Commit>()
    private val queue: MutableList<Hash> = ArrayList()
    private val MAX_ENTRIES = 50

    /**
     * Get a commit that belongs to the repository. The search is started from the head.
     */
    suspend fun getCommit(hashValue: Hash): Commit? {
        val commit = commitCache[hashValue]
        if (commit != null) {
            queue.remove(hashValue)
            queue.add(hashValue)
            return commit
        }
        return loadCommit(hashValue)
    }

    suspend private fun loadCommit(hashValue: Hash): Commit {
        val commit = Commit.read(hashValue, repository.objectIndex)
        commitCache[hashValue] = commit
        queue += hashValue
        while (queue.size > MAX_ENTRIES) {
            val old = queue.removeAt(0)
            commitCache.remove(old)
        }

        return commit
    }

    suspend fun isAncestor(baseCommit: Hash, ancestorCandidate: Hash): Boolean {
        val commitBox = getCommit(baseCommit) ?: throw Exception("Can't load commit: $baseCommit")
        for (parent in commitBox.parents) {
            val parentCommit = loadCommit(parent)
            val parentHash = parentCommit.getHash()
            if (parentHash == ancestorCandidate)
                return true
            if (isAncestor(parentHash, ancestorCandidate))
                return true
        }
        return false
    }
}
