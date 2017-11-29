package org.fejoa.repository

import org.fejoa.storage.HashValue
import org.fejoa.crypto.CryptoHelper


interface BranchLogIO {
    /**
     * @return an identifier to be publicly stored in the BranchLog
     */
    suspend fun logHash(repoRef: RepositoryRef): HashValue {
        val hashStream = CryptoHelper.sha256Hash()
        repoRef.write(hashStream)
        return HashValue(hashStream.hash())
    }

    /**
     * Serializes the latest object index chunk container ref.
     *
     * Here is also the place to encrypt/decrypt the objectIndexRef.
     */
    suspend fun writeToLog(repoRef: RepositoryRef): String
    suspend fun readFromLog(logEntry: String): RepositoryRef
}
