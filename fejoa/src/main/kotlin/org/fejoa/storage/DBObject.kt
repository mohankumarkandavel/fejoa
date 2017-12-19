package org.fejoa.storage

import org.fejoa.support.PathUtils


abstract class DBObject(val dir: IOStorageDir, var path: String) {
    constructor(parent: DBObject, relativePath: String)
            : this(parent.dir, PathUtils.appendDir(parent.path, relativePath))
}

abstract class DBValue<T>(dir: IOStorageDir, path: String) : DBObject(dir, path) {
    constructor(parent: DBObject, relativePath: String)
            : this(parent.dir, PathUtils.appendDir(parent.path, relativePath))

    abstract suspend fun write(obj: T)
    abstract suspend fun get(): T

    open suspend fun exists(): Boolean {
        return dir.probe(path) != IODatabase.FileType.NOT_EXISTING
    }

    suspend fun delete() {
        dir.remove(path)
    }
}
