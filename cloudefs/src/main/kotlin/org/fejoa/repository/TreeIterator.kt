package org.fejoa.repository

import org.fejoa.support.AsyncIterator
import org.fejoa.support.PathUtils


class TreeIterator(val includeAllAdded: Boolean, val includeAllRemoved: Boolean)
    : AsyncIterator<DiffIterator.Change<DirectoryEntry>> {
    private val iterators = ArrayList<Iterator<DiffIterator.Change<DirectoryEntry>>>()

    /**
     * @param includeAllAdded if a directory is added all child entries are iterated as well
     * @param includeAllRemoved if a directory is removed all child entries are iterated as well
     */
    constructor(ours: Directory, theirs: Directory, includeAllAdded: Boolean, includeAllRemoved: Boolean)
        : this(includeAllAdded, includeAllRemoved){
        iterators += DirectoryDiffIterator("", ours, theirs)
    }

    suspend override fun hasNext(): Boolean {
        while (iterators.isNotEmpty() && !iterators[0].hasNext())
            iterators.removeAt(0)
        return iterators.isNotEmpty()
    }

    suspend override fun next(): DiffIterator.Change<DirectoryEntry> {
        val next = iterators[0].next()
        if (next.type === DiffIterator.Type.MODIFIED && next.ours!!.isDir() && next.theirs!!.isDir()) {
            val ourSubDir = next.ours!! as Directory
            val theirSubDir = next.theirs!! as Directory
            iterators.add(DirectoryDiffIterator(next.path, ourSubDir, theirSubDir))
        } else if (includeAllAdded && next.type === DiffIterator.Type.ADDED && next.theirs!!.isDir()) {
            iterators.add(AddedDirIterator(next.path, next.theirs!! as Directory))
        } else if (includeAllRemoved && next.type === DiffIterator.Type.REMOVED && next.ours!!.isDir()) {
            iterators.add(RemovedDirIterator(next.path, next.ours!! as Directory))
        }
        return next
    }

    /**
     * Iterates over the content of an added directory and returns all sub entries as ADDED
     */
    private class AddedDirIterator(path: String, directory: Directory) : DirIterator(path, directory) {
        override fun createChange(path: String, entry: DirectoryEntry): DiffIterator.Change<DirectoryEntry> {
            return DiffIterator.Change.added(path, entry)
        }
    }

    /**
     * Iterates over the content of an removed directory and returns all sub entries as REMOVED
     */
    private class RemovedDirIterator(path: String, directory: Directory) : DirIterator(path, directory) {
        override fun createChange(path: String, entry: DirectoryEntry): DiffIterator.Change<DirectoryEntry> {
            return DiffIterator.Change.removed(path, entry)
        }
    }

    private abstract class DirIterator(path: String, directory: Directory) : Iterator<DiffIterator.Change<DirectoryEntry>> {
        private val iterators = ArrayList<Pair<String, Iterator<DirectoryEntry>>>()

        init {
            iterators.add(path to directory.getChildren().iterator())
        }

        abstract fun createChange(path: String, entry: DirectoryEntry): DiffIterator.Change<DirectoryEntry>

        override fun hasNext(): Boolean {
            while (iterators.isNotEmpty() && !iterators[0].second.hasNext())
                iterators.removeAt(0)
            return iterators.isNotEmpty()
        }

        override fun next(): DiffIterator.Change<DirectoryEntry> {
            val entry = iterators[0].second.next()
            val path = PathUtils.appendDir(iterators[0].first, entry.name)
            if (entry is Directory)
                iterators.add(path to entry.getChildren().iterator())

            return createChange(path, entry)
        }
    }
}
