package org.fejoa.repository

import org.fejoa.chunkcontainer.ChunkContainer
import org.fejoa.chunkcontainer.ChunkContainerNode
import org.fejoa.support.AsyncIterator
import org.fejoa.storage.HashValue
import org.fejoa.storage.ChunkTransaction
import org.fejoa.support.await


class ChunkInfo(val box: HashValue, val data: HashValue, val iv: ByteArray, var chunkType: ChunkType,
                var ccOrigin: CCOrigin) {
    enum class ChunkType {
        ROOT,
        LEAF,
        DATA_LEAF,
        DATA
    }

    enum class CCOrigin {
        UNSET,
        OBJECT_INDEX,
        COMMIT,
        DIR,
        BLOB
    }

    override fun toString(): String {
        return "Box: ${box.toHex().substring(0, 8)}, Data: ${data.toHex().substring(0, 8)}, IV: ${HashValue(iv).toHex().substring(0, 8)} Type: ${chunkType.name}, Origin: ${ccOrigin.name}"
    }
}

fun ChunkContainer.chunkIterator(ccOrigin: ChunkInfo.CCOrigin): AsyncIterator<ChunkInfo> {
    var hashes = ArrayList<ChunkInfo>()
    val ongoingNodes: MutableList<ChunkContainerNode> = ArrayList()
    ongoingNodes += this
    return object : AsyncIterator<ChunkInfo> {
        private fun add(node: ChunkContainerNode) {
            val currentType = when {
                node.isRootNode -> ChunkInfo.ChunkType.ROOT
                node.isDataLeafNode -> ChunkInfo.ChunkType.DATA_LEAF
                else -> ChunkInfo.ChunkType.LEAF
            }
            val pointer = node.that
            val info = ChunkInfo(pointer.boxHash, pointer.dataHash, pointer.iv, currentType, ccOrigin)
            hashes.add(info)
        }

        suspend override fun hasNext(): Boolean {
            if (hashes.isNotEmpty())
                return true
            if (ongoingNodes.isEmpty())
                return false

            // add children
            val current = ongoingNodes.removeAt(0)
            if (current is ChunkContainer && !current.isShortData())
                add(current)
            if (!current.isDataLeafNode) {
                for (pointer in current.chunkPointers) {
                    val next = ChunkContainerNode.read(blobAccessor, current, pointer)
                    ongoingNodes.add(next)
                }
            } else {
                for (pointer in current.chunkPointers) {
                    val info = ChunkInfo(pointer.boxHash, pointer.dataHash, pointer.iv,
                            ChunkInfo.ChunkType.DATA, ccOrigin)
                    hashes.add(info)
                }
            }

            return true
        }

        suspend override fun next(): ChunkInfo {
            return hashes.removeAt(0)
        }
    }
}


suspend fun ObjectIndex.chunkIterator(): AsyncIterator<ChunkInfo> {
    val hashes = CombinedIterator<ChunkInfo>()
    hashes.add(this.chunkContainer.chunkIterator(ChunkInfo.CCOrigin.OBJECT_INDEX))
    val ongoing = this.listChunkContainers().iterator()
    return object : AsyncIterator<ChunkInfo> {
        suspend override fun hasNext(): Boolean {
            if (hashes.hasNext())
                return true
            if (!ongoing.hasNext())
                return false
            val next = ongoing.next()
            val ref = next.second
            val id = next.first
            val type = when {
                id.startsWith(ObjectIndex.COMMIT_ID) -> ChunkInfo.CCOrigin.COMMIT
                id.startsWith(ObjectIndex.TREE_ID) -> ChunkInfo.CCOrigin.DIR
                id.startsWith(ObjectIndex.BLOB_ID) -> ChunkInfo.CCOrigin.BLOB
                else -> throw Exception("Unknown id type: $id")
            }
            val container = ChunkContainer.read(getChunkAccessor(ref), ref)
            hashes.add(container.chunkIterator(type))
            return hashes.hasNext()
        }

        suspend override fun next(): ChunkInfo {
            return hashes.next()
        }
    }
}

suspend fun Repository.chunkIterator(): AsyncIterator<ChunkInfo> {
    return objectIndex.chunkIterator()
}

suspend fun Repository.gc(target: ChunkTransaction) {
    if (isModified())
        throw Exception("Only a clean repository can be garbage collected")

    val source = getCurrentTransaction().getRawAccessor()
    val iterator = chunkIterator()
    while (iterator.hasNext()) {
        val chunkHash = iterator.next().box
        val chunk = source.getChunk(chunkHash).await()
        target.putChunk(chunk).await()
    }
}