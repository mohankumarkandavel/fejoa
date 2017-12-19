package org.fejoa.storage


interface MergeStrategy {
    /**
     * Merges theirs into ours
     */
    suspend fun merge(ours: Database, theirs: Database)
}


class KeepOursUnchanged : MergeStrategy {
    suspend override fun merge(ours: Database, theirs: Database) {
        // Do nothing just keep our branch
    }
}