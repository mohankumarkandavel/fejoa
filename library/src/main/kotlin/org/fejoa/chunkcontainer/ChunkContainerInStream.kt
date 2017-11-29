package org.fejoa.chunkcontainer

import org.fejoa.support.AsyncInStream

import kotlin.math.min


class ChunkContainerInStream(private val container: ChunkContainer) : AsyncInStream {
    private var position: Long = 0
    private var chunkPosition: ChunkContainer.DataChunkPointer? = null

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var currentOffset = offset
        var totalBytesRead = 0
        var remaining = length
        while (totalBytesRead < length) {
            val bytesRead = readPartial(buffer, currentOffset, remaining)
            if (bytesRead < 0)
                return if (totalBytesRead > 0) totalBytesRead else -1
            currentOffset += bytesRead
            totalBytesRead += bytesRead
        }
        return totalBytesRead
    }

    protected suspend fun readPartial(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= container.getDataLength())
            return -1
        val current: DataChunk = validateCurrentChunk()
        val data = current.getData()
        val positionInChunk = (position - chunkPosition!!.position).toInt()
        val bytesToCopy = min((current.getDataLength() - positionInChunk).toInt(), length - offset)
        for (i in 0 until bytesToCopy)
            buffer[offset + i] = data[positionInChunk + i]

        position += bytesToCopy
        return bytesToCopy
    }

    suspend fun seek(position: Long) {
        this.position = position
        if (chunkPosition != null
                && (position >= chunkPosition!!.position + chunkPosition!!.getDataChunk().getDataLength()
                    || position < chunkPosition!!.position)) {
            chunkPosition = null
        }
    }

    suspend private fun validateCurrentChunk(): DataChunk {
        if (chunkPosition != null
                && position < chunkPosition!!.position + chunkPosition!!.getDataChunk().getDataLength()) {
            return chunkPosition!!.getDataChunk()
        }
        chunkPosition = container.get(position)
        return chunkPosition!!.getDataChunk()
    }
}
