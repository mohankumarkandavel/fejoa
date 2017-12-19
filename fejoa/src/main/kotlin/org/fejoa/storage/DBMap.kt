package org.fejoa.storage

import org.fejoa.support.PathUtils

abstract class DBMap<K, T : DBObject>(dir: IOStorageDir, path: String) : DBObject(dir, path) {
    constructor(parent: DBObject, relativePath: String)
            : this(parent.dir, PathUtils.appendDir(parent.path, relativePath))

    suspend abstract fun list(): Collection<String>
    abstract fun get(key: K): T
}
