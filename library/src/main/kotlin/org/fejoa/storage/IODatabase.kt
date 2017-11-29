package org.fejoa.storage


interface IODatabase {
    enum class FileType {
        FILE,
        DIRECTORY,
        NOT_EXISTING
    }

    suspend fun getHash(path: String): HashValue
    suspend fun probe(path: String): FileType
    suspend fun open(path: String, mode: RandomDataAccess.Mode): RandomDataAccess
    suspend fun remove(path: String)

    suspend fun listFiles(path: String): Collection<String>
    suspend fun listDirectories(path: String): Collection<String>

    suspend fun readBytes(path: String): ByteArray
    suspend fun putBytes(path: String, data: ByteArray)
}
