package org.fejoa.storage

import org.fejoa.support.PathUtils
import org.fejoa.support.readAll
import org.fejoa.support.toUTF
import org.fejoa.support.toUTFString


open class IOStorageDir(protected val database: IODatabase, baseDir: String) {
    var baseDir: String = baseDir
        private set

    constructor(storageDir: IOStorageDir, baseDir: String, absoluteBaseDir: Boolean = false)
        : this(storageDir.database,
            if (absoluteBaseDir)
                baseDir
            else
                PathUtils.appendDir(storageDir.baseDir, baseDir)
            )

    protected fun getRealPath(path: String): String {
        return PathUtils.appendDir(baseDir, path)
    }

    suspend fun getHash(path: String): HashValue {
        return database.getHash(path)
    }

    suspend fun probe(path: String): IODatabase.FileType {
        return database.probe(path)
    }

    suspend fun readBytes(path: String): ByteArray {
        return readBytes(database, getRealPath(path))
    }

    suspend fun writeBytes(path: String, bytes: ByteArray) {
        writeBytes(database, getRealPath(path), bytes)
    }

    suspend fun remove(path: String) {
        database.remove(getRealPath(path))
    }

    suspend fun listFiles(path: String): Collection<String> {
        return database.listFiles(getRealPath(path))
    }

    suspend fun listDirectories(path: String): Collection<String> {
        return database.listDirectories(getRealPath(path))
    }

    suspend fun readString(path: String): String {
        return readBytes(path).toUTFString()
    }

    suspend fun readInt(path: String): Int {
        return readString(path).toInt()
    }

    suspend fun readLong(path: String): Long {
        return readString(path).toLong()
    }

    suspend fun writeString(path: String, data: String) {
        writeBytes(path, data.toUTF())
    }

    suspend fun writeInt(path: String, data: Int) {
        var dataString = ""
        dataString += data
        writeString(path, dataString)
    }

    suspend fun writeLong(path: String, data: Long) {
        var dataString = ""
        dataString += data
        writeString(path, dataString)
    }

    suspend fun open(path: String, mode: RandomDataAccess.Mode): RandomDataAccess {
        return database.open(getRealPath(path), mode)
    }

    companion object {
        suspend fun readBytes(database: IODatabase, path: String): ByteArray {
            val randomDataAccess = database.open(path, RandomDataAccess.Mode.READ)
            val data = randomDataAccess.readAll()
            randomDataAccess.close()
            return data
        }

        suspend fun writeBytes(database: IODatabase, path: String, bytes: ByteArray) {
            val randomDataAccess = database.open(path, RandomDataAccess.Mode.TRUNCATE)
            randomDataAccess.write(bytes)
            randomDataAccess.close()
        }
    }
}
