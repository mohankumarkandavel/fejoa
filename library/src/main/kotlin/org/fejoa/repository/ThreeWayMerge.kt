package org.fejoa.repository

import org.fejoa.storage.Database
import org.fejoa.storage.IMergeStrategy


class ThreeWayMerge(val conflictSolver: IConflictSolver = TakeOursSolver()) : IMergeStrategy {
    class TakeOursSolver : ThreeWayMerge.IConflictSolver {
        override fun solve(path: String, ours: DirectoryEntry, theirs: DirectoryEntry): DirectoryEntry {
            return ours
        }
    }

    interface IConflictSolver {
        fun solve(path: String, ours: DirectoryEntry, theirs: DirectoryEntry): DirectoryEntry
    }

    suspend override fun merge(ours: Database, theirs: Database) {
        if (ours !is Repository || theirs !is Repository)
            throw Exception("Unsupported database type")
        val treeAccessor = ours.ioDatabase.treeAccessor
        val treeIterator = TreeIterator(ours.ioDatabase.getRootDirectory(), theirs.ioDatabase.getRootDirectory(),
                includeAllAdded = false, includeAllRemoved = false)
        while (treeIterator.hasNext()) {
            val change = treeIterator.next()
            if (change.type === DiffIterator.Type.ADDED) {
                treeAccessor.put(change.path, change.theirs!!)
            } else if (change.type === DiffIterator.Type.REMOVED) {
                treeAccessor.remove(change.path)
            } else if (change.type === DiffIterator.Type.MODIFIED) {
                if (change.ours!!.isDir())
                    continue
                treeAccessor.put(change.path, conflictSolver.solve(change.path, change.ours!!, change.theirs!!))
            }
        }
    }
}
