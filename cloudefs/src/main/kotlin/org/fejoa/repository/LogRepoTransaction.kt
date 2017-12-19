package org.fejoa.repository

import org.fejoa.chunkcontainer.ContainerSpec
import org.fejoa.storage.*
import org.fejoa.support.Future
import org.fejoa.support.Future.Companion.completedFuture


class LogRepoTransaction(private val childTransaction: ChunkAccessors.Transaction) : ChunkAccessors.Transaction {
    private val objectsWritten = ArrayList<HashValue>()

    override fun getRawAccessor(): ChunkTransaction {
        return createWrapper(childTransaction.getRawAccessor())
    }

    override fun getObjectIndexAccessor(spec: ContainerSpec): ChunkAccessor {
        return createWrapper(childTransaction.getObjectIndexAccessor(spec))
    }

    override fun getCommitAccessor(spec: ContainerSpec): ChunkAccessor {
        return createWrapper(childTransaction.getCommitAccessor(spec))
    }

    override fun getTreeAccessor(spec: ContainerSpec): ChunkAccessor {
        return createWrapper(childTransaction.getTreeAccessor(spec))
    }

    override fun getFileAccessor(spec: ContainerSpec, filePath: String): ChunkAccessor {
        return createWrapper(childTransaction.getFileAccessor(spec, filePath))
    }

    override fun finishTransaction(): Future<Unit> {
        return childTransaction.finishTransaction()
    }

    override fun cancel(): Future<Unit> {
        return childTransaction.cancel()
    }

    private fun createWrapper(parent: ChunkTransaction): ChunkTransaction {
        return object : ChunkTransaction {
            override fun getChunk(boxHash: HashValue): Future<ByteArray> = parent.getChunk(boxHash)
            override fun putChunk(data: ByteArray): Future<PutResult<HashValue>> {
                return parent.putChunk(data).then { result ->
                    if (!result.wasInDatabase)
                        objectsWritten.add(result.key)

                    return@then result
                }
            }
            override fun hasChunk(boxHash: HashValue): Future<Boolean> = parent.hasChunk(boxHash)

            override fun finishTransaction(): Future<Unit> {
                return parent.finishTransaction()
            }

            override fun cancel(): Future<Unit> {
                return parent.cancel()
            }
        }
    }

    private fun createWrapper(chunkAccessor: ChunkAccessor): ChunkAccessor {
        return object : ChunkAccessor {
            override fun getChunk(hash: ChunkRef): Future<ByteArray> {
                return chunkAccessor.getChunk(hash)
            }

            override fun putChunk(data: ByteArray, ivHash: HashValue): Future<PutResult<HashValue>> {
                return chunkAccessor.putChunk(data, ivHash).then { result ->
                    if (!result.wasInDatabase)
                        objectsWritten.add(result.key)

                    return@then result
                }
            }

            override fun releaseChunk(data: HashValue): Future<Unit> {
                for (written in objectsWritten) {
                    if (written != data)
                        continue
                    objectsWritten.remove(written)
                    break
                }
                return completedFuture(Unit)
            }
        }
    }

    fun getObjectsWritten(): List<HashValue> {
        return objectsWritten
    }
}
