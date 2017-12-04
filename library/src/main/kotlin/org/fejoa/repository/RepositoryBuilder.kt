package org.fejoa.repository

import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.SecretKey
import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.support.*


object RepositoryBuilder {
    fun getPlainBranchLogIO(): BranchLogIO = object: BranchLogIO {
        suspend override fun writeToLog(repoRef: RepositoryRef): String {
            val buffer = commitPointerToLog(repoRef)
            return buffer.encodeBase64()
        }

        override suspend fun readFromLog(logEntry: String): RepositoryRef {
            val logEntryBytes = logEntry.decodeBase64()
            return commitPointerFromLog(logEntryBytes)
        }
    }

    suspend private fun commitPointerToLog(repoRef: RepositoryRef): ByteArray {
        val buffer = ProtocolBufferLight()
        repoRef.write(buffer)
        return buffer.toByteArray()
    }

    suspend private fun commitPointerFromLog(bytes: ByteArray): RepositoryRef {
        val buffer = ProtocolBufferLight(bytes)
        return RepositoryRef.read(buffer)
    }

    private val TAG_IV = 0
    private val TAG_ENCDATA = 1

    fun getEncryptedBranchLogIO(key: SecretKey, symmetric: CryptoSettings.Symmetric)
            : BranchLogIO = object: BranchLogIO {

        override suspend fun writeToLog(repoRef: RepositoryRef): String {
            val buffer = commitPointerToLog(repoRef)
            val crypto = CryptoHelper.crypto
            val iv = crypto.generateInitializationVector(symmetric.ivSize)
            val encryptedMessage = crypto.encryptSymmetric(buffer, key, iv, symmetric).await()
            val protoBuffer = ProtocolBufferLight()
            protoBuffer.put(TAG_IV, iv)
            protoBuffer.put(TAG_ENCDATA, encryptedMessage)
            return protoBuffer.toByteArray().encodeBase64()
        }

        override suspend fun readFromLog(logEntry: String): RepositoryRef {
            val protoBuffer = ProtocolBufferLight(logEntry.decodeBase64())
            val iv = protoBuffer.getBytes(TAG_IV) ?: throw Exception("IV expected")
            val encData = protoBuffer.getBytes(TAG_ENCDATA) ?: throw Exception("Encrypted data expected")
            val plain = CryptoHelper.crypto.decryptSymmetric(encData, key, iv, symmetric).await()
            return commitPointerFromLog(plain)
        }
    }
}
