package org.fejoa.chunkcontainer

import org.fejoa.chunkcontainer.ChunkHash.Companion.DATA_LEAF_LEVEL
import org.fejoa.chunkcontainer.ChunkHash.Companion.DATA_LEVEL
import org.fejoa.crypto.CryptoHelper

import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.storage.*
import org.fejoa.support.*


interface NodeSplitterFactory {
    fun nodeSizeFactor(level: Int): Float
    fun create(level: Int): ChunkSplitter
}

open class ChunkContainerNode : Chunk {
    val that: ChunkPointer
    protected val hashOutStream: AsyncHashOutStream
    var onDisk = false
    var parent: ChunkContainerNode? = null
    val blobAccessor: ChunkAccessor
    private var data: ByteArray? = null
    private var dataHash: HashValue? = null
    private val slots = ArrayList<ChunkPointer>()
    val nodeWriter: NodeWriteStrategy

    protected constructor(blobAccessor: ChunkAccessor, parent: ChunkContainerNode?, nodeWriter: NodeWriteStrategy,
                          level: Int, compact: Boolean, hashOutStream: AsyncHashOutStream) {
        this.blobAccessor = blobAccessor
        this.parent = parent
        this.that = ChunkPointer(this, level, compact)
        this.hashOutStream = hashOutStream
        this.nodeWriter = nodeWriter.newInstance(level)
        resetNodeSplitter()
    }

    private constructor(blobAccessor: ChunkAccessor, parent: ChunkContainerNode, nodeWriter: NodeWriteStrategy,
                        that: ChunkPointer, hashOutStream: AsyncHashOutStream) {
        this.blobAccessor = blobAccessor
        this.parent = parent
        this.that = that
        this.hashOutStream = hashOutStream
        this.nodeWriter = nodeWriter.newInstance(level)
        resetNodeSplitter()
    }

    private fun resetNodeSplitter() {
        nodeWriter.reset(level)
        // Write dummy to later split at the right position.
        // For example:
        // |dummy|h1|h2|h3 (containing the trigger t)|
        // this results in the node: |h1|h2|h3|..t|
        nodeWriter.splitter.write(ByteArray(Config.DATA_HASH_SIZE))
    }

    val isRootNode: Boolean
        get() = parent == null
    val isDataLeafNode: Boolean
        get() = that.getLevel() === DATA_LEAF_LEVEL

    open protected val headerLength: Int
        get() = 0

    override fun getDataLength(): Long {
        return calculateDataLength()
    }

    suspend protected fun getDataChunk(pointer: ChunkPointer): DataChunk {
        assert(isDataPointer(pointer))
        val cachedChunk = pointer.getCachedChunk()
        if (cachedChunk != null)
            return cachedChunk as DataChunk
        val chunk = blobAccessor.getChunk(pointer).await()
        val inputStream = ByteArrayInStream(chunk)
        val dataChunk = DataChunk()
        dataChunk.read(inputStream, pointer.dataLength)
        pointer.setCachedChunk(dataChunk)
        return dataChunk
    }

    suspend fun getNode(pointer: ChunkPointer): ChunkContainerNode {
        assert(!isDataPointer(pointer))
        val cachedChunk = pointer.getCachedChunk()
        if (cachedChunk != null)
            return cachedChunk as ChunkContainerNode

        assert(slots.contains(pointer))

        val node = ChunkContainerNode.read(blobAccessor, this, pointer)
        pointer.setCachedChunk(node)
        return node
    }

    protected fun calculateDataLength(): Long {
        var length: Long = 0
        for (pointer in slots)
            length += pointer.dataLength
        return length
    }

    val level: Int
        get() = that.getLevel()

    /**
     * @param pointerDataPosition absolute position of the pointer that contains the searched data
     */
    protected class SearchResult(val pointerDataPosition: Long, val pointer: ChunkPointer?, val node: ChunkContainerNode)

    /**
     *
     * @param dataPosition relative to this node
     * @return
     */
    protected fun findInNode(node: ChunkContainerNode, dataPosition: Long): SearchResult? {
        if (dataPosition > node.getDataLength())
            return null

        var position: Long = 0
        for (i in node.slots.indices) {
            val pointer = node.slots[i]
            val dataLength = pointer.dataLength
            if (position + dataLength > dataPosition)
                return SearchResult(position, pointer, node)
            position += dataLength
        }
        return SearchResult(position, null, node)
    }

    suspend override fun read(inputStream: InStream, dataLength: Long) {
        slots.clear()
        var dataLengthRead: Long = 0
        while (dataLengthRead < dataLength) {
            val pointer = ChunkPointer(level = that.getLevel() - 1, compact = that.compact)
            pointer.read(inputStream)
            dataLengthRead += pointer.dataLength.toInt()
            addBlobPointer(pointer)
        }
        if (dataLengthRead != dataLength) {
            throw IOException("Chunk container node addresses " + dataLengthRead + " bytes but " + dataLength
                    + " bytes expected")
        }
        onDisk = true
    }

    suspend private fun findRightNeighbour(): ChunkContainerNode? {
        if (parent == null)
            return null

        // find common parent
        var indexInParent = -1
        var parent: ChunkContainerNode? = this
        var levelDiff = 0
        while (parent!!.parent != null) {
            levelDiff++
            val pointerInParent = parent.that
            parent = parent.parent
            indexInParent = parent!!.indexOf(pointerInParent)
            assert(indexInParent >= 0)
            if (indexInParent != parent.size() - 1)
                break
        }

        // is last pointer?
        if (indexInParent == parent.size() - 1)
            return null

        var neighbour = parent.getNode(parent[indexInParent + 1])
        for (i in 0..levelDiff - 1 - 1)
            neighbour = neighbour.getNode(neighbour[0])

        assert(neighbour.that.getLevel() === that.getLevel())
        return neighbour
    }

    /**
     * Returns the split pair.
     *
     * The left node is usually the input node (this). It differs only when the root node is split.
     */
    suspend private fun splitAt(index: Int): Pair<ChunkContainerNode, ChunkContainerNode> {
        val right = ChunkContainerNode.create(blobAccessor, parent, nodeWriter, that.getLevel(),
                that.compact, hashOutStream)
        while (size() > index)
            right.addBlobPointer(removeBlobPointer(index))
        if (parent != null) {
            val inParentIndex = parent!!.indexOf(that)
            parent!!.addBlobPointer(inParentIndex + 1, right.that)
            return this to right
        } else {
            // we are the root node and we want to stay so, thus move the items to a new child
            val left = ChunkContainerNode.create(blobAccessor, parent, nodeWriter, that.getLevel(),
                    that.compact, hashOutStream)
            while (size() > 0)
                left.addBlobPointer(removeBlobPointer(0))
            addBlobPointer(left.that)
            addBlobPointer(right.that)
            that.setLevel(that.getLevel() + 1)
            return left to right
        }
    }

    /**
     * The first element of the returned pair is the balanced node. If the node has been split the second field contains
     * the remaining part of the node.
     */
    suspend private fun balance(): Pair<ChunkContainerNode, ChunkContainerNode?> {
        resetNodeSplitter()

        val size = size()
        for (i in 0..size - 1) {
            val child = get(i)
            nodeWriter.splitter.write(child.dataHash.bytes)
            if (nodeWriter.splitter.isTriggered) {
                // split leftover into a right node
                if (i == size - 1) // all good
                    return this to null
                // split left over into a right node
                return splitAt(i + 1)
            }
        }

        // we are not full; get pointers from the right neighbour till we are full
        findRightNeighbour()?.let {
            var neighbour = it
            while (neighbour.size() > 0) {
                var nextNeighbour: ChunkContainerNode? = null
                // we need one item to find the next right neighbour
                if (neighbour.size() == 1)
                    nextNeighbour = neighbour.findRightNeighbour()

                // The neighbour node pointers might be dirty so flush them first.
                // The neighbour can't be removed now because flushChildren requires an intact tree
                val pointer = neighbour[0]
                if (that.getLevel() > DATA_LEAF_LEVEL)
                    pointer.flushChildren()

                neighbour.removeBlobPointer(0, true)
                addBlobPointer(pointer)
                nodeWriter.splitter.write(pointer.dataHash.bytes)
                if (nodeWriter.splitter.isTriggered)
                    break

                if (nextNeighbour != null)
                    neighbour = nextNeighbour
            }
        }

        return this to null
    }

    private val root: ChunkContainerNode
        get() {
            var root: ChunkContainerNode? = this
            while (root!!.parent != null)
                root = root.parent
            return root
        }

    private val isRedundantRoot: Boolean
        get() {
            if (level == DATA_LEAF_LEVEL)
                return false
            return slots.size <= 1
        }

    /**
     * The idea is to flush item from the left to the right. The bottommost level is flushed first
     *
     * @param childOnly only child nodes are flushed
     * @throws IOException
     */
    open suspend fun flush(childOnly: Boolean) {
        assert(parent == null)

        var level = -1
        // while root changes because child nodes have been split do:
        while (level != that.getLevel()) {
            level = that.getLevel()
            flushChildren(childOnly)
        }

        // remove redundant root nodes
        // TODO don't write redundant nodes in the first place
        while (isRedundantRoot) {
            val onlyChild = slots[0].getCachedChunk() as ChunkContainerNode
            removeBlobPointer(0)
            while (onlyChild.slots.size > 0)
                addBlobPointer(onlyChild.removeBlobPointer(0))
            that.setLevel(this.level - 1)
        }

        if (!childOnly)
            writeNode()
    }

    suspend protected fun ChunkPointer.flushChildren() {
        val chunk: Chunk = this.getCachedChunk() ?: return
        assert(chunk is ChunkContainerNode)
        val node = chunk as ChunkContainerNode
        if (node.onDisk)
            return
        node.flushChildren(false)
    }

    suspend private fun flushChildren(childOnly: Boolean) {
        if (childOnly)
            assert(parent == null)

        // its assumed that all leaf data chunks are already on disk; just flush/balance the tree
        val level = that.getLevel()
        if (level > DATA_LEAF_LEVEL) {
            // IMPORTANT: the slot size may grow/shrink when flushing the child so check in each iteration!
            var i = 0
            while (i < slots.size)  {
                val pointer = slots[i]
                i++

                pointer.flushChildren()
            }
        }

        // do the real work balance the node and write it to disk
        val balancedNode = balance().first
        if (!childOnly || balancedNode != this)
            balancedNode.writeNode()
    }

    /**
     * @param dataHash the data hash (without the meta data)
     * @param chunkHash the hash of chunk that will be written
     */
    private fun getIV(dataHash: ByteArray, chunkHash: ByteArray): HashValue {
        val iv = if (that.compact)
            dataHash
        else
            chunkHash
        return HashValue(iv)
    }

    suspend protected fun writeNode() {
        if (onDisk)
            return
        val oldBoxHash = that.boxHash
        val data = getData()
        val dataHash = hash(hashOutStream)
        val rawHash = rawHash()

        val result = blobAccessor.putChunk(data, getIV(dataHash.bytes, rawHash.bytes)).await()
        val boxHash = result.key

        // cleanup old chunk
        if (boxHash != oldBoxHash && !oldBoxHash.isZero)
            blobAccessor.releaseChunk(oldBoxHash)

        if (parent != null)
            parent!!.invalidate()

        that.setTo(dataHash, boxHash, rawHash)

        onDisk = true
    }

    override fun getData(): ByteArray {
        if (data != null)
            return data!!
        val outputStream = ByteArrayOutStream()
        for (pointer in slots)
            pointer.write(outputStream)
        data = nodeWriter.finalizeWrite(outputStream.toByteArray())
        return data!!
    }

    suspend fun toStringAsync(): String {
        var string = "Data Hash: " + hash(hashOutStream) + "\n"
        for (pointer in slots)
            string += pointer.toString() + "\n"
        return string
    }

    suspend open fun printAll(): String {
        var string = toStringAsync()

        if (that.getLevel() === DATA_LEAF_LEVEL)
            return string

        for (pointer in slots)
            string += getNode(pointer).printAll()
        return string
    }

    suspend fun hash(): HashValue {
        return hash(hashOutStream)
    }

    override suspend fun hash(hashOutStream: AsyncHashOutStream): HashValue {
        if (dataHash == null)
            dataHash = calculateDataHash(hashOutStream)
        return dataHash!!
    }

    suspend protected fun rawHash(): HashValue {
        return HashValue(CryptoHelper.hash(getData(), hashOutStream))
    }

    suspend private fun calculateDataHash(hashOutStream: AsyncHashOutStream): HashValue {
        // if there is only one data chunk use this hash
        if (parent == null && slots.size == 1)
            return slots[0].dataHash
        hashOutStream.reset()
        for (pointer in slots)
            hashOutStream.write(pointer.dataHash.bytes)
        return HashValue(hashOutStream.hash())
    }

    protected fun invalidate() {
        data = null
        dataHash = null
        onDisk = false
        if (parent != null)
            parent!!.invalidate()
    }

    suspend fun addBlobPointer(index: Int, pointer: ChunkPointer) {
        slots.add(index, pointer)
        if (!isDataPointer(pointer) && pointer.getCachedChunk() != null)
            (pointer.getCachedChunk() as ChunkContainerNode).parent = this
        invalidate()
    }

    suspend protected fun addBlobPointer(pointer: ChunkPointer) {
        addBlobPointer(slots.size, pointer)
    }

    suspend fun removeBlobPointer(i: Int, updateParentsIfEmpty: Boolean = false): ChunkPointer {
        val pointer = slots.removeAt(i)
        invalidate()

        if (updateParentsIfEmpty && parent != null && slots.size == 0) {
            val inParentIndex = parent!!.indexOf(that)
            parent!!.removeBlobPointer(inParentIndex, true)
        }
        return pointer
    }

    fun size(): Int {
        return slots.size
    }

    operator fun get(index: Int): ChunkPointer {
        return slots[index]
    }

    val chunkPointers: List<ChunkPointer>
        get() = slots

    fun indexOf(pointer: ChunkPointer): Int {
        return slots.indexOf(pointer)
    }

    suspend fun clear() {
        slots.clear()
        that.setLevel(DATA_LEAF_LEVEL)
        invalidate()
    }

    companion object {
        fun create(blobAccessor: ChunkAccessor, parent: ChunkContainerNode?, nodeWriter: NodeWriteStrategy,
                   level: Int, compact: Boolean, hashOutStream: AsyncHashOutStream): ChunkContainerNode {
            return ChunkContainerNode(blobAccessor, parent, nodeWriter, level, compact, hashOutStream)
        }

        suspend fun read(blobAccessor: ChunkAccessor, parent: ChunkContainerNode, that: ChunkPointer)
                : ChunkContainerNode {
            val node = ChunkContainerNode(blobAccessor, parent, parent.nodeWriter, that,
                    parent.hashOutStream)
            val chunk = blobAccessor.getChunk(that).await()
            node.read(ByteArrayInStream(chunk), that.dataLength)
            return node
        }

        fun isDataPointer(pointer: ChunkPointer): Boolean {
            return pointer.getLevel() === DATA_LEVEL
        }
    }
}
