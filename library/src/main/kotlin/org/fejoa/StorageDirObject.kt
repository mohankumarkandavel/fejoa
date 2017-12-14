package org.fejoa

import org.fejoa.storage.StorageDir
import org.fejoa.support.toUTF


open class StorageDirObject(val storageDir: StorageDir) {
    val branch: String
        get() = storageDir.branch

    open suspend fun commit() {
        storageDir.commit("Client commit".toUTF())
    }
}