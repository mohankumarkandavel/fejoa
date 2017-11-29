package org.fejoa.chunkcontainer

import org.fejoa.storage.ChunkSplitter
import org.fejoa.support.*
import kotlin.math.round


class RepeaterStream(val buffer: ByteArray) : InStream {
    private var source = ByteArrayInStream(buffer)
    override fun read(): Int {
        val result = source.read()
        if (result >= 0)
            return result

        // start again
        source = ByteArrayInStream(buffer)
        return source.read()
    }
}

interface NodeWriteStrategy {
    val splitter: ChunkSplitter
    fun newInstance(level: Int): NodeWriteStrategy
    fun reset(level: Int)
    /**
     * Finalizes the output, e.g. to normalized the chunk size.
     *
     * It is assumed that the data is already processed by the current splitter. It is save to use the current splitter
     * for the normalization.
     */
    fun finalizeWrite(data: ByteArray): ByteArray
}

/**
 * @param normalizeChunkSize all chunks should follow a certain size distribution
 */
abstract class NodeWriteStrategyBase(val nodeSplitterFactory: NodeSplitterFactory, var level: Int,
                                     val normalizeChunkSize: Boolean) : NodeWriteStrategy {
    protected var chunkSplitter = nodeSplitterFactory.create(level)

    override val splitter: ChunkSplitter
        get() = chunkSplitter

    override fun reset(level: Int) {
        if (this.level != level) {
            chunkSplitter = nodeSplitterFactory.create(level)
            this.level = level
        } else {
            chunkSplitter.reset()
        }
    }
}


class DynamicNodeWriteStrategy(nodeSplitterFactory: NodeSplitterFactory, level: Int, normalizeChunkSize: Boolean = false)
    : NodeWriteStrategyBase(nodeSplitterFactory, level, normalizeChunkSize) {

    override fun newInstance(level: Int): NodeWriteStrategy {
        return DynamicNodeWriteStrategy(nodeSplitterFactory, level)
    }

    override fun finalizeWrite(data: ByteArray): ByteArray {
        // Equalize:
        // For example: assume a node sizeFactor of 2/3 and a node that triggered at byte t:
        // |123t|
        // This is scaled up by 3/2: 4 * 3/2 = 6. The additional space is filled with random values r:
        // |123trr|

        if (!normalizeChunkSize)
            return data


        val outStream = ByteArrayOutStream()
        var bytesWritten = outStream.write(data)
        val repeater = RepeaterStream(data)

        val random = Random()
        // ensure that we trigger
        while (!chunkSplitter.isTriggered) {
            chunkSplitter.write(random.read().toByte())
            outStream.write(repeater.readByte())
            bytesWritten++
        }

        val plainSize = chunkSplitter.triggerPosition + 1
        val outSize = round(plainSize / nodeSplitterFactory.nodeSizeFactor(level)).toInt()
        // fill remaining space till we get the outSize
        while (bytesWritten < outSize) {
            chunkSplitter.write(random.read().toByte())
            outStream.write(repeater.readByte())
            bytesWritten ++
        }
        return outStream.toByteArray()
    }
}

class FixedSizeNodeWriteStrategy(val nodeSize: Int, nodeSplitterFactory: NodeSplitterFactory, level: Int,
                                 normalizeChunkSize: Boolean = false)
    : NodeWriteStrategyBase(nodeSplitterFactory, level, normalizeChunkSize) {

    override fun newInstance(level: Int): NodeWriteStrategy {
        return FixedSizeNodeWriteStrategy(nodeSize, nodeSplitterFactory, level, normalizeChunkSize)
    }

    override fun finalizeWrite(data: ByteArray): ByteArray {
        if (!normalizeChunkSize)
            return data

        val random = Random()
        val outStream = ByteArrayOutStream()
        var bytesWritten = outStream.write(data)
        val repeater = RepeaterStream(data)
        // fill remaining space with random bytes till we get the outSize
        while (bytesWritten < nodeSize) {
            chunkSplitter.write(random.read().toByte())
            outStream.write(repeater.readByte())
            bytesWritten ++
        }
        return outStream.toByteArray()
    }
}