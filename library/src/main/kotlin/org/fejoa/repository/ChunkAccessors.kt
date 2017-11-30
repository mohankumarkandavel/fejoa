package org.fejoa.repository

import org.fejoa.chunkcontainer.BoxSpec
import org.fejoa.chunkcontainer.ContainerSpec
import org.fejoa.crypto.CryptoHelper
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


class RepoChunkAccessors(val storage: ChunkStorage, val repoConfig: RepositoryConfig = RepositoryConfig())
    : ChunkAccessors {

    override fun startTransaction(): ChunkAccessors.Transaction {
        return object : ChunkAccessors.Transaction {
            val chunkStorageTransaction = storage.startTransaction()

            private fun prepareEncryption(accessor: ChunkAccessor): ChunkAccessor {
                return when (repoConfig.boxSpec.encInfo.type) {
                    BoxSpec.EncryptionInfo.Type.PLAIN -> accessor
                    BoxSpec.EncryptionInfo.Type.PARENT -> {
                        val cryptoConfig = repoConfig.crypto ?: throw Exception("Missing crypto data")
                        accessor.encrypted(CryptoHelper.crypto, cryptoConfig.secretKey, cryptoConfig.symmetric)
                    }
                }
            }

            private fun prepareCompression(accessor: ChunkAccessor): ChunkAccessor {
                return when (repoConfig.boxSpec.zipType) {
                    BoxSpec.ZipType.NONE -> accessor
                    BoxSpec.ZipType.DEFLATE -> accessor.compressed()
                }
            }

            private fun prepareAccessor(): ChunkAccessor {
                var accessor = chunkStorageTransaction.toChunkAccessor()
                if (repoConfig.boxSpec.zipBeforeEnc) {
                    // we have to prepare in reverse oder:
                    accessor = prepareEncryption(accessor)
                    accessor = prepareCompression(accessor)
                } else {
                    accessor = prepareCompression(accessor)
                    accessor = prepareEncryption(accessor)
                }
                return accessor
            }

            override fun getRawAccessor(): ChunkTransaction {
                return chunkStorageTransaction
            }

            override fun getTreeAccessor(spec: ContainerSpec): ChunkAccessor {
                return prepareAccessor()
            }

            override fun getFileAccessor(spec: ContainerSpec, filePath: String): ChunkAccessor {
                return prepareAccessor()
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
