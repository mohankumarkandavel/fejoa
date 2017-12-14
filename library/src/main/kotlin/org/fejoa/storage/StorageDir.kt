package org.fejoa.storage

import org.fejoa.chunkcontainer.Hash
import org.fejoa.repository.CommitSignature
import org.fejoa.support.Executor
import org.fejoa.support.toUTF
import org.fejoa.support.toUTFString


class StorageDir : IOStorageDir {
    constructor(storageDir: StorageDir) : this(storageDir, storageDir.baseDir, true)

    constructor(storageDir: StorageDir, path: String, absoluteBaseDir: Boolean = false)
            : super(storageDir, path, absoluteBaseDir) {
        parent = storageDir
        storageDir.childDirs.add(this)
    }

    constructor(database: Database, baseDir: String, commitSignature: CommitSignature?, listenerExecutor: Executor)
            : super(StorageDirCache(database, commitSignature, listenerExecutor), baseDir)

    private val storageDirCache: StorageDirCache
        get() = database as StorageDirCache

    suspend fun getHead(): Hash {
        return storageDirCache.getHead()
    }

    val branch: String
        get() = storageDirCache.getBranch()

    suspend fun setCommitSignature(commitSignature: CommitSignature?) {
        storageDirCache.commitSignature = commitSignature
    }

    interface IListener {
        fun onTipChanged(diff: DatabaseDiff)
    }

    // the directory this directory is derived from
    private var parent: StorageDir? = null
    // dirs derived from this directory
    private val childDirs: MutableList<StorageDir> = ArrayList()
    // listeners associated with this StorageDir
    private val localListeners : MutableList<IListener> = ArrayList()

    fun addListener(listener: IListener) {
        localListeners.add(listener)
        storageDirCache.addListener(listener)
    }

    fun removeListener(listener: IListener) {
        localListeners.remove(listener)
        storageDirCache.removeListener(listener)
    }

    suspend fun close() {
        localListeners.forEach { storageDirCache.removeListener(it) }
        localListeners.clear()

        childDirs.forEach { it.close() }
        val that = this
        parent?.also { it.childDirs.remove(that) }
    }

    /**
     * The StorageDirCache is shared between all StorageDir that are build from the same parent.
     */
    internal class StorageDirCache(database: Database, var commitSignature: CommitSignature?,
                                   val listenerExecutor: Executor?) : Database by database {
        private val listeners: MutableList<StorageDir.IListener> = ArrayList()

        private fun notifyTipChanged(diff: DatabaseDiff) {
            for (listener in listeners) {
                if (listenerExecutor != null) {
                    listenerExecutor.run {
                        listener.onTipChanged(diff)
                    }
                } else {
                    listener.onTipChanged(diff)
                }
            }
        }

        fun addListener(listener: IListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: IListener) {
            listeners.remove(listener)
        }

        suspend fun commit(message: ByteArray) {
            val base = getHead()

            commit(message, commitSignature)

            if (listeners.isNotEmpty()) {
                val tip = getHead()
                val diff = getDiff(base.value, tip.value)
                notifyTipChanged(diff)
            }
        }

        suspend fun onTipUpdated(old: HashValue, newTip: HashValue) {
            if (listeners.isNotEmpty()) {
                val diff = getDiff(old, newTip)
                notifyTipChanged(diff)
            }
        }
    }

    suspend fun commit(message: ByteArray = "Client commit".toUTF()) {
        storageDirCache.commit(message)
    }

    suspend fun getDiff(baseCommit: HashValue, endCommit: HashValue): DatabaseDiff {
        return storageDirCache.getDiff(baseCommit, endCommit)
    }

    suspend fun onTipUpdated(old: HashValue, newTip: HashValue) {
        storageDirCache.onTipUpdated(old, newTip)
    }

    suspend fun printContent(path: String, nSubDir: Int,
                             dataPrinter: (data: ByteArray) -> String = { it.toUTFString() }): String {
        var out = ""
        for (dir in listDirectories(path)) {
            for (i in 0 until nSubDir)
                out += "  "
            out += dir + "\n"
            out += printContent(path + "/" + dir, nSubDir + 1, dataPrinter)
        }
        for (file in listFiles(path)) {
            for (i in 0 until nSubDir)
                out += "  "
            out += file
            out += " -> "
            out += dataPrinter.invoke(readBytes(path + "/" + file))
            out += "\n"
        }
        return out
    }
}
