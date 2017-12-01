package org.fejoa.chunkcontainer

import org.fejoa.crypto.HashOutStreamFactory

import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.storage.ChunkSplitter
import org.fejoa.storage.Config
import org.fejoa.storage.HashValue


class ChunkHash(dataSplitter: ChunkSplitter,
                private val nodeSplitter: ChunkSplitter,
                private val hashOutStreamFactory: HashOutStreamFactory) : AsyncHashOutStream {
    companion object {
        val DATA_LEVEL = 0
        val DATA_LEAF_LEVEL = 1

        suspend fun create(nodeSplitterFactory: NodeSplitterFactory, hashOutStreamFactory: HashOutStreamFactory)
                : ChunkHash {
            return ChunkHash(nodeSplitterFactory.create(DATA_LEVEL), nodeSplitterFactory.create(DATA_LEAF_LEVEL),
                    hashOutStreamFactory)
        }

        suspend fun create(hashSpec: HashSpec): ChunkHash {
            return create(hashSpec.getNodeSplitterFactory(), hashSpec.getBaseHashFactory())
        }
    }

    private var currentLayer: Layer = Layer(dataSplitter, false, DATA_LEVEL)

    private fun getHashOutStream(): BufferedHash {
        return BufferedHash(hashOutStreamFactory.create())
    }

    private class BufferedHash(internal val hash: AsyncHashOutStream?) {
        internal var buffer = ByteArray(1024 * 128)
        internal var bufferSize = 0

        suspend fun write(b: Byte) {
            buffer[bufferSize] = b
            bufferSize++
            if (bufferSize == buffer.size) {
                hash!!.write(buffer)
                bufferSize = 0
            }
        }

        suspend fun hash(): ByteArray {
            if (bufferSize > 0) {
                hash!!.write(buffer, 0, bufferSize)
                bufferSize = 0
            }
            return hash!!.hash()
        }

        suspend fun reset() {
            bufferSize = 0
            hash!!.reset()
        }
    }

    private inner class Layer(val splitter: ChunkSplitter, val isNode: Boolean, val level: Int): AsyncHashOutStream {
        private var upperLayer: Layer? = null
        private var cachedUpperLayer: Layer? = null
        protected var hash: BufferedHash? = null
        // dataHash of the first chunk, only if there are more then one chunk an upper layer is started
        private var firstChunkHash: ByteArray? = null

        init {
            resetSplitter()
        }

        override fun reset() {
            resetSplitter()
            if (this.upperLayer != null) {
                this.upperLayer!!.reset()
                this.cachedUpperLayer = this.upperLayer
            }
            this.upperLayer = null
            this.hash = null
            this.firstChunkHash = null
        }

        protected fun resetSplitter() {
            this.splitter.reset()
            if (isNode) {
                // Write dummy to later split at the right position.
                // For example:
                // |dummy|h1|h2|h3 (containing the trigger t)|
                // this results in the node: |h1|h2|h3|..t|
                this.splitter.write(ByteArray(Config.DATA_HASH_SIZE))
            }
        }

        suspend override fun write(data: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || offset + length > data.size)
                throw IllegalArgumentException()

            if (hash == null)
                hash = getHashOutStream()

            for (i in offset until offset + length) {
                val b = data[i]
                hash!!.write(b)
                splitter.update(b)
                if (splitter.isTriggered) {
                    resetSplitter()
                    finalizeChunk()
                    if (i < data.size - 1)
                        hash = getHashOutStream()
                }
            }
            return length
        }

        suspend override fun write(data: ByteArray): Int {
            return write(data, 0, data.size)
        }

        var hashes = ArrayList<HashValue>()
        suspend fun updateNode(hashData: ByteArray) {
            if (hash == null)
                hash = getHashOutStream()

            hashes.add(HashValue(hashData))
            splitter.write(hashData)
            // hash data
            for (b in hashData)
                hash!!.write(b)

            if (splitter.isTriggered) {
                hashes.add(HashValue(ByteArray(0)))
                resetSplitter()
                finalizeChunk()
            }
        }

        suspend protected fun finalizeChunk() {
            if (hash == null)
                return

            val chunkHash = hash!!.hash()

            if (firstChunkHash == null && upperLayer == null)
                firstChunkHash = chunkHash
            else {
                val upper = ensureUpperLayer()
                if (firstChunkHash != null) {
                    upper.updateNode(firstChunkHash!!)
                    firstChunkHash = null
                }
                upper.updateNode(chunkHash)
            }
            hash = null
        }

        suspend override fun hash(): ByteArray {
            finalizeChunk()
            if (firstChunkHash != null)
                return firstChunkHash!!
            // empty data
            return upperLayer?.hash() ?: ByteArray(0)
        }

        internal fun ensureUpperLayer(): Layer {
            if (upperLayer == null) {
                if (cachedUpperLayer != null)
                    upperLayer = cachedUpperLayer
                else {
                    val nextLevel = level + 1
                    val splitter = newNodeSplitter()
                    upperLayer = Layer(splitter, true, nextLevel)
                }
            }
            return upperLayer!!
        }

        suspend override fun flush() {

        }

        suspend override fun close() {
        }
    }

    init {
        dataSplitter.reset()
        nodeSplitter.reset()

        reset()
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): Int {
        return currentLayer.write(data, offset, length)
    }

    suspend fun write(data: Byte): Int {
        val buffer = ByteArray(1)
        buffer[0] = data
        return write(buffer)
    }

    override suspend fun hash(): ByteArray {
        return currentLayer.hash()
    }

    override fun reset() {
        currentLayer.reset()
    }

    protected fun newNodeSplitter(): ChunkSplitter {
        return nodeSplitter.newInstance()
    }

    suspend override fun flush() {
    }

    suspend override fun close() {
    }
}
