package org.fejoa.storage

import org.fejoa.support.PathUtils


class DBString(dir: IOStorageDir, path: String) : DBValue<String>(dir, path) {
    constructor(parent: DBObject, relativePath: String)
            : this(parent.dir, PathUtils.appendDir(parent.path, relativePath))

    suspend override fun write(obj: String) {
        dir.writeString(path, obj)
    }

    suspend override fun get(): String {
        return dir.readString(path)
    }
}
