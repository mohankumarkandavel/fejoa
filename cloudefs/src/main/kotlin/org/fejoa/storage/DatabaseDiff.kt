package org.fejoa.storage


class DatabaseDiff(val base: HashValue, val target: HashValue, val changes: Collection<Entry>) {
    enum class ChangeType {
        ADDED,
        REMOVED,
        MODIFIED
    }

    class Entry(val path: String, val type: ChangeType)
}
