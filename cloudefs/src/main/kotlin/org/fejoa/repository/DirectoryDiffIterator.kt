package org.fejoa.repository


class DirectoryDiffIterator(basePath: String, ours: Directory?, theirs: Directory)
    : DiffIterator<DirectoryEntry>(basePath, ours?.getChildren() ?: emptyList(), theirs.getChildren(),
        object : DiffIterator.NameGetter<DirectoryEntry> {
            override fun getName(entry: DirectoryEntry): String {
                return entry.name
            }
        })
