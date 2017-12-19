package org.fejoa.storage

import org.fejoa.support.PathUtils

class DBHashValue(dir: IOStorageDir, path: String) : DBValue<HashValue>(dir, path) {
    constructor(parent: DBObject, relativePath: String)
            : this(parent.dir, PathUtils.appendDir(parent.path, relativePath))

    suspend override fun write(obj: HashValue) {
        dir.writeString(path, obj.toHex())
    }

    suspend override fun get(): HashValue {
        return HashValue.fromHex(dir.readString(path))
    }
}
