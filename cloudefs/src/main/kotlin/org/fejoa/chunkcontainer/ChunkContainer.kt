package org.fejoa.chunkcontainer

import org.fejoa.chunkcontainer.ChunkHash.Companion.DATA_LEAF_LEVEL
import org.fejoa.chunkcontainer.ChunkHash.Companion.DATA_LEVEL
import org.fejoa.storage.*
import org.fejoa.storage.Config.IV_SIZE
import org.fejoa.support.*


class ChunkPointer(dataHash: HashValue, boxHash: HashValue, iv: HashValue, dataLength: Long, blob: Chunk? = null,
                   level: Int = 0, val compact: Boolean) : ChunkRef(dataHash, boxHash, iv, dataLength) {
    constructor(blob: Chunk? = null, level: Int = 0, compact: Boolean)
            : this(Config.newDataHash(), Config.newBoxHash(), HashValue(Config.newIV()),
                    0L, blob, level, compact)

    companion object {
        private val LENGTH_SIZE = 8

        val nodePointerLength: Int
            get() = LENGTH_SIZE + Config.DATA_HASH_SIZE + Config.BOX_HASH_SIZE + IV_SIZE

        val compactNodePointerLength: Int
            get() = LENGTH_SIZE + Config.DATA_HASH_SIZE + Config.BOX_HASH_SIZE
    }

    private var cachedChunk: Chunk?
    private var level: Int

    override val iv: ByteArray
        get() {
            return if (!compact)
                super.iv
            else
                dataHash.bytes.copyOfRange(0, Config.IV_SIZE)
        }

    init {
        this.cachedChunk = blob
        this.level = level
    }

    fun getCachedChunk(): Chunk? {
        return cachedChunk
    }

    fun setCachedChunk(chunk: Chunk?) {
        this.cachedChunk = chunk
    }

    fun getLevel(): Int {
        return level
    }

    fun setLevel(level: Int) {
        this.level = level
    }

    override var dataLength: Long = 0L
        get() {
            cachedChunk?.let {
                field = it.getDataLength()
            }
            return field
        }


    fun read(inputStream: InStream) {
        this.dataLength = inputStream.readLong()
        inputStream.readFully(dataHash.bytes)
        inputStream.readFully(boxHash.bytes)
        // Data level point can use the dataHash as an IV. Only nodes need their own IV in case they get re-encrypted in
        // which case they would reuse the same IV for encryption.
        if (!compact)
            inputStream.readFully(super.iv)
    }

    fun write(outputStream: OutStream): Int {
        // update the data length
        var bytesWritten = 0
        bytesWritten += outputStream.writeLong(dataLength)
        bytesWritten += outputStream.write(dataHash.bytes)
        bytesWritten += outputStream.write(boxHash.bytes)
        if (!compact)
            bytesWritten += outputStream.write(super.iv)
        return bytesWritten
    }

    override fun toString(): String {
        var string = "l:" + this.dataLength
        if (!dataHash.isZero)
            string += " (data:" + dataHash.toString() + " box:" + boxHash.toString() + ")"
        return string
    }
}


internal class CacheManager(private val chunkContainer: ChunkContainer) {
    private class PointerEntry(val dataChunkPointer: ChunkPointer, val parent: ChunkContainerNode) : DoubleLinkedList.Entry<PointerEntry>()

    private val queue: DoubleLinkedList<PointerEntry> = DoubleLinkedList()
    private val pointerMap: MutableMap<ChunkPointer, PointerEntry> = HashMap()

    private val targetCapacity = 10
    private val triggerCapacity = 15
    private val keptMetadataLevels = 2

    private fun bringToFront(entry: PointerEntry) {
        queue.remove(entry)
        queue.addFirst(entry)
    }

    fun update(dataChunkPointer: ChunkPointer, parent: ChunkContainerNode) {
        assert(ChunkContainerNode.isDataPointer(dataChunkPointer))
        var entry: PointerEntry? = pointerMap[dataChunkPointer]
        if (entry != null) {
            bringToFront(entry)
            return
        }
        entry = PointerEntry(dataChunkPointer, parent)
        queue.addFirst(entry)
        pointerMap.put(dataChunkPointer, entry)
        if (pointerMap.size >= triggerCapacity)
            clean(triggerCapacity - targetCapacity)
    }

    fun remove(dataChunkPointer: ChunkPointer) {
        val entry = pointerMap[dataChunkPointer] ?: return
        queue.remove(entry)
        pointerMap.remove(dataChunkPointer)
        // don't clean parents yet, they are most likely being edited right now
    }

    private fun clean(numberOfEntries: Int) {
        for (i in 0 until numberOfEntries) {
            val entry = queue.removeTail() ?: throw Exception("Unexpected null")
            pointerMap.remove(entry.dataChunkPointer)
            clean(entry)
        }
    }

    private fun clean(entry: PointerEntry) {
        // always clean the data cache
        entry.dataChunkPointer.setCachedChunk(null)

        var currentPointer = entry.dataChunkPointer
        var currentParent: ChunkContainerNode? = entry.parent
        while (currentParent != null && chunkContainer.level - currentParent.level >= keptMetadataLevels) {
            currentPointer.setCachedChunk(null)
            if (hasCachedPointers(currentParent))
                break

            currentPointer = currentParent.that
            currentParent = currentParent.parent
        }
    }

    private fun hasCachedPointers(node: ChunkContainerNode): Boolean {
        for (pointer in node.chunkPointers) {
            if (pointer.getCachedChunk() != null)
                return true
        }
        return false
    }
}


class ContainerSpec(val hashSpec: HashSpec, val boxSpec: BoxSpec)


/**
 * Container for data chunks.
 *
 * Data chunks are split externally, i.e. in ChunkContainerOutStream. However, all leaf nodes are managed here.
 */
class ChunkContainer : ChunkContainerNode {
    val ref: ChunkContainerRef
    private val cacheManager: CacheManager = CacheManager(this)

    /**
     * Create a new chunk container.
     *
     * @param blobAccessor
     */
    private constructor(blobAccessor: ChunkAccessor, ref: ChunkContainerRef)
            : super(blobAccessor, null, ref.hash.spec.getNodeWriteStrategy(ref.boxSpec.nodeNormalization),
                    DATA_LEAF_LEVEL, ref.hash.spec.compact, ref.hash.spec.getBaseHashOutStream()) {
        this.ref = ref
    }

    companion object {
        fun create(blobAccessor: ChunkAccessor, spec: ContainerSpec)
                : ChunkContainer {
            val ref = ChunkContainerRef(spec.hashSpec, spec.boxSpec)
            return ChunkContainer(blobAccessor, ref)
        }

        suspend fun read(blobAccessor: ChunkAccessor, ref: ChunkContainerRef)
                : ChunkContainer {
            val chunkContainer = ChunkContainer(blobAccessor, ref)
            chunkContainer.that.setTo(ref.dataHash, ref.boxHash, ref.iv)

            var level = ref.nLevel
            if (level > DATA_LEVEL) {
                chunkContainer.that.setLevel(level)
                val chunk = blobAccessor.getChunk(ref.chunkRef).await()
                chunkContainer.read(ByteArrayInStream(chunk), ref.length)
            } else if (!ref.boxHash.isZero){
                // this means there is exactly one data chunk
                val chunk = blobAccessor.getChunk(ref.chunkRef).await()
                chunkContainer.append(DataChunk(chunk, ref.chunkRef.dataLength.toInt()))
            }
            return chunkContainer
        }

        val compactNodeSplittingRatio: Float
            get() = Config.DATA_HASH_SIZE.toFloat() / ChunkPointer.compactNodePointerLength
        val nodeSplittingRatio: Float
            get() = Config.DATA_HASH_SIZE.toFloat() / ChunkPointer.nodePointerLength
    }

    suspend override fun flush(childOnly: Boolean) {
        if (!isShortData()) {
            super.flush(childOnly)

            ref.hash.value = that.dataHash
            ref.boxHash = that.boxHash
            ref.iv = that.iv

            ref.nLevel = level
        } else {
            if (size() > 0) {
                val dataChunk = get(0)
                ref.hash.value = dataChunk.dataHash
                ref.boxHash = dataChunk.boxHash
                ref.iv = dataChunk.iv
            } else {
                // the container is empty
                ref.hash.value = Config.newDataHash()
                ref.boxHash = Config.newBoxHash()
                ref.iv = Config.newIV()
            }
            // There is only one data chunk, this is indicated by the DATA_LEVEL level.
            ref.nLevel = DATA_LEVEL
        }

        ref.length = getDataLength()
    }

    /**
     * Indicates if data fit into a single chunk.
     *
     * In this case only the data chunk is stored and no extra chunk for the chunk container is needed
     */
    fun isShortData(): Boolean {
        return level == DATA_LEAF_LEVEL && size() <= 1
    }

    inner class DataChunkPointer(private val pointer: ChunkPointer, val position: Long) {
        private var cachedChunk: DataChunk? = null
        val dataLength: Long = pointer.dataLength

        suspend fun getDataChunk(): DataChunk {
            val chunk = this@ChunkContainer.getDataChunk(pointer)
            if (cachedChunk == null)
                cachedChunk = chunk
            return cachedChunk!!
        }
    }

    fun getChunkIterator(startPosition: Long): AsyncIterator<DataChunkPointer> {
        return object : AsyncIterator<DataChunkPointer> {
            private var position = startPosition

            suspend override fun hasNext(): Boolean {
                return position < getDataLength()
            }

            suspend override fun next(): DataChunkPointer {
                val dataChunkPointer = get(position)
                position = dataChunkPointer.position + dataChunkPointer.dataLength
                return dataChunkPointer
            }
        }
    }

    suspend fun get(position: Long): DataChunkPointer {
        val searchResult = findLevel0Node(position)
        if (searchResult.pointer == null)
            throw IOException("Invalid position: " + position)
        cacheManager.update(searchResult.pointer, searchResult.node)
        return DataChunkPointer(searchResult.pointer, searchResult.pointerDataPosition)
    }

    suspend private fun findLevel0Node(position: Long): SearchResult {
        var currentPosition: Long = 0
        var containerNode: ChunkContainerNode = this
        var pointer: ChunkPointer? = null
        for (i in 0 until that.getLevel()) {
            val result = findInNode(containerNode, position - currentPosition) ?: // find right most node blob
                    return SearchResult(getDataLength(), null, findRightMostNode())
            currentPosition += result.pointerDataPosition
            pointer = result.pointer ?: throw Exception("Internal error")
            if (i == that.getLevel() - 1)
                break
            else
                containerNode = containerNode.getNode(pointer)
        }

        return SearchResult(currentPosition, pointer, containerNode)
    }

    suspend private fun findRightMostNode(): ChunkContainerNode {
        var current: ChunkContainerNode = this
        for (i in 0..that.getLevel() - 1 - 1) {
            val pointer = current[current.size() - 1]
            current = current.getNode(pointer)
        }
        return current
    }

    suspend private fun putDataChunk(blob: DataChunk): ChunkPointer {
        val rawBlob = blob.getData()
        val hash = blob.hash(hashOutStream)
        val result = blobAccessor.putChunk(rawBlob, hash).await()
        val boxedHash = result.key
        return ChunkPointer(hash, boxedHash, hash, rawBlob.size.toLong(), blob, DATA_LEVEL,
                ref.hash.spec.compact)
    }

    internal class InsertSearchResult(val containerNode: ChunkContainerNode, val index: Int)

    suspend private fun findInsertPosition(position: Long): InsertSearchResult {
        var currentPosition: Long = 0
        var node: ChunkContainerNode = this
        var index = 0
        for (i in 0 until that.getLevel()) {
            var nodePosition: Long = 0
            val inNodeInsertPosition = position - currentPosition
            index = 0
            var pointer: ChunkPointer? = null
            while (index < node.size()) {
                pointer = node.get(index)
                val dataLength = pointer.dataLength
                if (nodePosition + dataLength > inNodeInsertPosition)
                    break

                // Don't advance when at the last pointer in the node (this also means the insert point is at the end of
                // the node). Only advanced when the data level is reached.
                if (index < node.size() - 1 || i == that.getLevel() - 1)
                    nodePosition += dataLength
                index++
            }
            currentPosition += nodePosition
            if (nodePosition > inNodeInsertPosition || i == that.getLevel() - 1
                    && nodePosition != inNodeInsertPosition) {
                throw IOException("Invalid insert position")
            }

            if (i < that.getLevel() - 1)
                node = node.getNode(pointer!!)
        }

        return InsertSearchResult(node, index)
    }

    suspend fun insert(blob: DataChunk, position: Long): ChunkPointer {
        val searchResult = findInsertPosition(position)
        val containerNode = searchResult.containerNode
        val blobChunkPointer = putDataChunk(blob)
        containerNode.addBlobPointer(searchResult.index, blobChunkPointer)

        cacheManager.update(blobChunkPointer, containerNode)
        return blobChunkPointer
    }

    suspend fun append(blob: DataChunk): ChunkPointer {
        return insert(blob, getDataLength())
    }

    suspend fun remove(position: Long, dataChunk: DataChunk) {
        remove(position, dataChunk.getDataLength())
    }

    suspend fun remove(position: Long, length: Long) {
        val searchResult = findLevel0Node(position)
        if (searchResult.pointer == null)
            throw IOException("Invalid position")
        if (searchResult.pointerDataPosition != position)
            throw IOException("Invalid position. Position must be at a chunk boundary")
        if (searchResult.pointer.dataLength !== length)
            throw IOException("Data length mismatch")

        val containerNode = searchResult.node
        val indexInParent = containerNode.indexOf(searchResult.pointer)
        containerNode.removeBlobPointer(indexInParent, true)

        cacheManager.remove(searchResult.pointer)
    }

    // 1 byte for number of levels
    override val headerLength: Int
        get() = 1

    suspend override fun printAll(): String {
        var string = "Levels=" + that.getLevel() + ", length=" + getDataLength() + "\n"
        string += super.printAll()
        return string
    }
}
