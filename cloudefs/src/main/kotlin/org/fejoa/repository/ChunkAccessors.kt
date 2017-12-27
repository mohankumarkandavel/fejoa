package org.fejoa.repository

import org.fejoa.chunkcontainer.BoxSpec
import org.fejoa.chunkcontainer.ContainerSpec
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.SymBaseCredentials
import org.fejoa.storage.*
import org.fejoa.support.Future


interface ChunkAccessors {
    interface Transaction : StorageTransaction {
        /**
         * Accessor to raw data chunks.
         *
         * This accessor provides direct access to the chunk store.
         */
        fun getRawAccessor(): ChunkTransaction

        /**
         * Accessor to access commit chunks.
         */
        fun getObjectIndexAccessor(spec: ContainerSpec): ChunkAccessor {
            return getTreeAccessor(spec)
        }

        /**
         * Accessor to access commit chunks.
         */
        fun getCommitAccessor(spec: ContainerSpec): ChunkAccessor {
            return getTreeAccessor(spec)
        }

        /**
         * Accessor to access the directory structure chunks.
         */
        fun getTreeAccessor(spec: ContainerSpec): ChunkAccessor

        /**
         * Accessor to access the files structure chunks.
         */
        fun getFileAccessor(spec: ContainerSpec, filePath: String): ChunkAccessor
    }

    fun startTransaction(): Transaction
}


private fun prepareEncryption(accessor: ChunkAccessor, boxSpec: BoxSpec, crypto: SymBaseCredentials?): ChunkAccessor {
    return when (boxSpec.encInfo.type) {
        BoxSpec.EncryptionInfo.Type.PLAIN -> accessor
        BoxSpec.EncryptionInfo.Type.PARENT -> {
            val cryptoConfig = crypto ?: throw Exception("Missing crypto data")
            accessor.encrypted(CryptoHelper.crypto, cryptoConfig.key, cryptoConfig.settings)
        }
    }
}

private fun prepareCompression(accessor: ChunkAccessor, boxSpec: BoxSpec): ChunkAccessor {
    return when (boxSpec.zipType) {
        BoxSpec.ZipType.NONE -> accessor
        BoxSpec.ZipType.DEFLATE -> accessor.compressed()
    }
}

fun ChunkAccessor.prepareAccessor(boxSpec: BoxSpec, crypto: SymBaseCredentials?): ChunkAccessor {
    var accessor = this
    if (boxSpec.zipBeforeEnc) {
        // we have to prepare in reverse oder:
        accessor = prepareEncryption(accessor, boxSpec, crypto)
        accessor = prepareCompression(accessor, boxSpec)
    } else {
        accessor = prepareCompression(accessor, boxSpec)
        accessor = prepareEncryption(accessor, boxSpec, crypto)
    }
    return accessor
}

class RepoChunkAccessors(val storage: ChunkStorage, val repoConfig: RepositoryConfig,
                         val crypto: SymBaseCredentials?)
    : ChunkAccessors {

    override fun startTransaction(): ChunkAccessors.Transaction {
        return object : ChunkAccessors.Transaction {
            val chunkStorageTransaction = storage.startTransaction()

            override fun getRawAccessor(): ChunkTransaction {
                return chunkStorageTransaction
            }

            override fun getTreeAccessor(spec: ContainerSpec): ChunkAccessor {
                return chunkStorageTransaction.toChunkAccessor().prepareAccessor(repoConfig.boxSpec, crypto)
            }

            override fun getFileAccessor(spec: ContainerSpec, filePath: String): ChunkAccessor {
                return chunkStorageTransaction.toChunkAccessor().prepareAccessor(repoConfig.boxSpec, crypto)
            }

            override fun finishTransaction(): Future<Unit> {
                return chunkStorageTransaction.cancel()
            }

            override fun cancel(): Future<Unit> {
                return chunkStorageTransaction.cancel()
            }
        }
    }
}
