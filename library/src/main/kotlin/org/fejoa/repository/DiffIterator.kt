package org.fejoa.repository

import org.fejoa.support.PathUtils


/**
 * Iterates changes relative to ours
 */
open class DiffIterator<T>(private val basePath: String, ours: Collection<T>, theirs: Collection<T>,
                      internal val nameGetter: NameGetter<T>) : Iterator<DiffIterator.Change<T>> {
    private val entryComparator = object : Comparator<T> {
        override fun compare(a: T, b: T): Int {
            return nameGetter.getName(a).compareTo(nameGetter.getName(b))
        }
    }
    private val oursEntries: MutableList<T> = ArrayList(ours)
    private val theirsEntries: MutableList<T> = ArrayList(theirs)
    private var ourIndex = 0
    private var theirIndex = 0
    private var next: Change<T>? = null

    init {
        oursEntries.sortWith(entryComparator)
        theirsEntries.sortWith(entryComparator)

        gotoNext()
    }

    interface NameGetter<in T> {
        fun getName(entry: T): String
    }

    enum class Type {
        ADDED,
        REMOVED,
        MODIFIED
    }

    class Change<T> private constructor(val type: Type, val path: String, val ours: T? = null,
                                            val theirs: T? = null) {
        companion object {

            fun <T> added(path: String, theirs: T): Change<T> {
                val change = Change(Type.ADDED, path, theirs = theirs)
                return change
            }

            fun <T> removed(path: String, ours: T): Change<T> {
                val change = Change(Type.REMOVED, path, ours = ours)
                return change
            }

            fun <T> modified(path: String, ours: T, theirs: T): Change<T> {
                val change = Change(Type.MODIFIED, path, ours, theirs)
                return change
            }
        }
    }

    private fun gotoNext() {
        next = null
        while (next == null) {
            var ourEntry: T? = null
            var theirEntry: T? = null
            if (ourIndex < oursEntries.size)
                ourEntry = oursEntries.get(ourIndex)
            if (theirIndex < theirsEntries.size)
                theirEntry = theirsEntries.get(theirIndex)
            if (ourEntry == null && theirEntry == null)
                break
            val compareValue: Int
            if (ourEntry == null)
                compareValue = 1
            else if (theirEntry == null)
                compareValue = -1
            else
                compareValue = entryComparator.compare(ourEntry, theirEntry)

            if (compareValue == 0) {
                theirIndex++
                ourIndex++
                if (ourEntry != theirEntry) {
                    next = Change.modified(PathUtils.appendDir(basePath, nameGetter.getName(ourEntry!!)), ourEntry,
                            theirEntry!!)
                    break
                }
                continue
            } else if (compareValue > 0) {
                // added
                theirIndex++
                next = Change.added(PathUtils.appendDir(basePath, nameGetter.getName(theirEntry!!)), theirEntry)
                break
            } else {
                // removed
                ourIndex++
                next = Change.removed(PathUtils.appendDir(basePath, nameGetter.getName(ourEntry!!)), ourEntry)
                break

            }
        }
    }

    override fun hasNext(): Boolean {
        return next != null
    }

    override fun next(): Change<T> {
        val current = next
        gotoNext()
        return current!!
    }
}
