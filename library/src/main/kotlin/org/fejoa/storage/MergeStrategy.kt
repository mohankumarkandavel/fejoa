package org.fejoa.storage


interface IMergeStrategy {
    /**
     * Merges theirs into ours
     */
    suspend fun merge(ours: Database, theirs: Database)
}


class KeepOursUnchanged : IMergeStrategy {
    suspend override fun merge(ours: Database, theirs: Database) {
        // Do nothing just keep our branch
    }
}