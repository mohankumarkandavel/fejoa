package org.fejoa.repository

import org.fejoa.storage.HashValue
import org.fejoa.crypto.CryptoHelper
import org.fejoa.protocolbufferlight.ProtocolBufferLight


interface BranchLogIO {
    /**
     * @return an identifier to be publicly stored in the BranchLog
     */
    suspend fun logHash(repoRef: RepositoryRef): HashValue {
        val protoBuffer = ProtocolBufferLight()
        repoRef.write(protoBuffer)

        val hashStream = CryptoHelper.sha256Hash()
        hashStream.write(protoBuffer.toByteArray())
        return HashValue(hashStream.hash())
    }

    /**
     * Serializes the latest object index chunk container ref.
     *
     * Here is also the place to encrypt/decrypt the objectIndexRef.
     */
    suspend fun writeToLog(repoRef: RepositoryRef): ByteArray
    suspend fun readFromLog(logEntry: ByteArray): RepositoryRef
}
