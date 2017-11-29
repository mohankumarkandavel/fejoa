package org.fejoa.storage

import org.fejoa.chunkcontainer.Hash
import org.fejoa.repository.CommitSignature


interface Database : IODatabase {
    enum class MergeResult constructor(val value: Int) {
        MERGED(0), // the merge may have modified the the repository; changes need to be committed
        FAST_FORWARD(1) // the head has been fast forward to the latest commit
    }

    fun getBranch(): String

    suspend fun getHead(): Hash
    suspend fun commit(message: ByteArray, signature: CommitSignature?): Hash
    /**
     * Merges one or more databases into our database.
     *
     * All involved databases may have uncommitted changes. If the databases don't have uncommitted changes the head
     * may be fast forwarded to the latest commit (if possible). In this case the merge don't need to be committed.
     */
    suspend fun merge(mergeParents: Collection<Database>, mergeStrategy: IMergeStrategy): MergeResult
    suspend fun getDiff(baseCommit: HashValue, endCommit: HashValue): DatabaseDiff
}
