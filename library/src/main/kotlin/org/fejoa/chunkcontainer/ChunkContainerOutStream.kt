package org.fejoa.chunkcontainer

import org.fejoa.support.AsyncOutStream
import org.fejoa.storage.RandomDataAccess
import org.fejoa.support.ByteArrayOutStream
import org.fejoa.support.IOException


/**
 * @param normalizeChunkSize data chunks are normalized. This option is ignored if writeStrategy is not null.
 */
class ChunkContainerOutStream(private val container: ChunkContainer,
                              private val mode: RandomDataAccess.Mode = RandomDataAccess.Mode.APPEND,
                              writeStrategy: NodeWriteStrategy? = null,
                              normalizeChunkSize: Boolean = false) : AsyncOutStream {
    private val writeStrategy = writeStrategy
            ?: container.ref.hash.spec.getNodeWriteStrategy(normalizeChunkSize)
    private var currentTransaction: ITransaction? = null
    private var position: Long = 0

    init {
        if (!mode.has(RandomDataAccess.Mode.WRITE))
            throw Exception("Invalid mode")
        if (mode.has(RandomDataAccess.Mode.INSERT)) {
            this.position = 0
        } else {
            // seek to end
            this.position = container.getDataLength()
        }
    }

    private interface ITransaction {
        suspend fun write(buffer: ByteArray, offset: Int, length: Int)

        fun position(): Long
        // returns the last position
        suspend fun finish(): Long
    }

    suspend private fun createInsertTransaction(seekPosition: Long, containerSize: Long): InsertTransaction {
        val transaction = InsertTransaction()
        transaction.goToStart(seekPosition, containerSize)
        return transaction
    }

    internal inner class InsertTransaction : ITransaction {
        private var startChunk: DataChunk? = null
        private var writtenInStartChunk: Int = 0

        private var writeStartPosition = 0L
        private var bytesFlushed = 0L
        private var bytesWritten = 0L

        private var outputStream = ByteArrayOutStream()

        suspend fun goToStart(seekPosition: Long, containerSize: Long) {
            writeStrategy.reset(ChunkHash.DATA_LEVEL)

            if (containerSize == 0L)
                return

            var start = seekPosition
            // recalculate the last chunk if we append data because the last chunk may not be full
            if (seekPosition == containerSize)
                start--

            val chunkPointer = container.get(start)
            startChunk = chunkPointer.getDataChunk()

            container.remove(chunkPointer.position, chunkPointer.dataLength)
            writeStartPosition = chunkPointer.position

            write(startChunk!!.getData(), 0, (seekPosition - writeStartPosition).toInt())
            writtenInStartChunk = (seekPosition - writeStartPosition).toInt()
        }

        suspend fun delete(length: Long) {
            var currentChunk = startChunk!!
            var remainingBytesToDelete = length
            var currentPosInChunk = position() - writeStartPosition
            var bytesToKeep = -1L
            while (remainingBytesToDelete > 0) {
                val bytesAvailable = currentChunk.getData().size - currentPosInChunk
                if (bytesAvailable >= remainingBytesToDelete) {
                    bytesToKeep = bytesAvailable - remainingBytesToDelete
                    remainingBytesToDelete = 0
                } else {
                    remainingBytesToDelete -= bytesAvailable
                    currentPosInChunk = 0

                    val chunkPointer = container.get(writeStartPosition)
                    currentChunk = chunkPointer.getDataChunk()
                    container.remove(chunkPointer.position, chunkPointer.dataLength)
                }
            }
            startChunk = currentChunk
            writtenInStartChunk = (startChunk!!.getData().size - bytesToKeep).toInt()
        }

        suspend override fun write(buffer: ByteArray, offset: Int, length: Int) {
            writeInternal(buffer, offset, length)
        }

        /**
         * Returns true if the last byte was on the chunk boundary
         */
        suspend private fun writeInternal(buffer: ByteArray, offset: Int, length: Int): Boolean {
            if (offset < 0 || length < 0 || offset + length > buffer.size)
                throw Exception("Index out of bounds")

            var splitOnLastByte = false
            val splitter = writeStrategy.getSplitter()
            for (i in offset until offset + length) {
                val byte = buffer[i]
                outputStream.write(byte)
                bytesWritten++
                if (splitter.update(byte)) {
                    flushChunk()
                    writeStrategy.reset(ChunkHash.DATA_LEVEL)
                    if (i == offset + length - 1)
                        splitOnLastByte = true
                }
            }
            return splitOnLastByte
        }

        suspend private fun flushChunk() {
            var data = outputStream.toByteArray()
            if (data.isEmpty())
                return
            // length before finalization
            val dataLength = data.size

            data = writeStrategy.finalizeWrite(data)
            container.insert(DataChunk(data, dataLength), writeStartPosition + bytesFlushed)
            // use the original data length, not the finalized length
            bytesFlushed += dataLength
            outputStream = ByteArrayOutStream()
        }

        override fun position(): Long {
            return writeStartPosition + bytesWritten
        }

        suspend override fun finish(): Long {
            val pos = position()

            // write remaining bytes from the first chunk
            if (startChunk != null) {
                val done = writeInternal(startChunk!!.getData(), writtenInStartChunk,
                        (startChunk!!.getDataLength() - writtenInStartChunk).toInt())
                //if (done)
                  //  return pos
            }

            // overwrite till we finish at a chunk boundary
            while (writeStartPosition + bytesFlushed < container.getDataLength()) {
                val chunkPointer = container.get(writeStartPosition + bytesFlushed)
                val chunk = chunkPointer.getDataChunk()
                container.remove(chunkPointer.position, chunkPointer.dataLength)
                val done = writeInternal(chunk.getData(), 0, chunk.getDataLength().toInt())
                if (done)
                    break
            }

            // flush last bit
            flushChunk()
            return pos
        }

    }

    suspend private fun createOverwriteTransaction(seekPosition: Long, containerSize: Long): OverwriteTransaction {
        val transaction = OverwriteTransaction()
        transaction.goToStart(seekPosition, containerSize)
        return transaction
    }

    internal inner class OverwriteTransaction : ITransaction {
        // position of the first chunk that is overwritten
        private var writeStartPosition: Long = 0
        private var bytesFlushed: Long = 0
        private var bytesDeleted: Long = 0
        // bytes written relative to writeStartPosition
        private var bytesWritten: Long = 0
        private var appending = false
        private var lastDeletedPointer: ChunkContainer.DataChunkPointer? = null
        private var outputStream = ByteArrayOutStream()

        suspend fun goToStart(seekPosition: Long, containerSize: Long) {
            writeStrategy.reset(ChunkHash.DATA_LEVEL)
            if (containerSize == 0L)
                return

            var start = seekPosition
            // recalculate the last chunk if we append data because the last chunk may not be full
            if (seekPosition == containerSize)
                start--

            lastDeletedPointer = container.get(start)
            val chunk = lastDeletedPointer!!.getDataChunk()

            removeChunk(lastDeletedPointer!!.position, lastDeletedPointer!!.dataLength)
            writeStartPosition = lastDeletedPointer!!.position

            write(chunk.getData(), 0, (seekPosition - writeStartPosition).toInt())
        }

        suspend private fun removeChunk(position: Long, size: Long) {
            container.remove(position, size)
            bytesDeleted += size
        }

        suspend private fun overwriteNextChunk() {
            if (appending)
                return
            val nextPosition = writeStartPosition + bytesFlushed
            if (nextPosition == container.getDataLength()) {
                lastDeletedPointer = null
                appending = true
                return
            }
            lastDeletedPointer = container.get(nextPosition)
            removeChunk(lastDeletedPointer!!.position, lastDeletedPointer!!.dataLength)
        }

        suspend private fun flushChunk() {
            var data = outputStream.toByteArray()
            if (data.isEmpty())
                return
            // length before finalization
            val dataLength = data.size
            data = writeStrategy.finalizeWrite(data)
            container.insert(DataChunk(data, dataLength), writeStartPosition + bytesFlushed)
            // use the original data length, not the finalized length
            bytesFlushed += dataLength
            if (bytesFlushed == bytesDeleted) {
                lastDeletedPointer = null
            } else {
                while (!appending && bytesFlushed > bytesDeleted) {
                    overwriteNextChunk()
                }
            }

            outputStream = ByteArrayOutStream()
        }

        suspend override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || offset + length > buffer.size)
                throw Exception("Index out of bounds")

            val splitter = writeStrategy.getSplitter()
            for (i in offset until offset + length) {
                // Prepare to overwrite the next chunk. This needs to be done in each iteration since the previous write
                // could have triggered a flush.
                if (lastDeletedPointer == null)
                    overwriteNextChunk()
                val byte = buffer[i]
                outputStream.write(byte)
                bytesWritten++
                if (splitter.update(byte)) {
                    flushChunk()
                    writeStrategy.reset(ChunkHash.DATA_LEVEL)
                }
            }
        }

        override fun position(): Long {
            return writeStartPosition + bytesWritten
        }

        suspend override fun finish(): Long {
            val pos = position()
            // write remaining data till we reached the end or a known chunk position
            while (lastDeletedPointer != null) {
                val data = lastDeletedPointer!!.getDataChunk().getData()
                val bytesToWrite = bytesDeleted - bytesWritten
                if (bytesToWrite <= 0)
                    break
                val start = data.size - bytesToWrite
                write(data, start.toInt(), bytesToWrite.toInt())
            }
            flushChunk()
            return pos
        }
    }

    suspend fun seek(position: Long) {
        // seek is also used to start a new transaction so only skip the seek if there is a current transaction
        if (currentTransaction != null && position() == position)
            return

        if (currentTransaction != null)
            this.position = currentTransaction!!.finish()

        val length = container.getDataLength()
        if (position > length || position < 0)
            throw IOException("Invalid seek position: $position (Length: $length)")

        if (mode.has(RandomDataAccess.Mode.INSERT))
            currentTransaction = createInsertTransaction(position, length)
        else
            currentTransaction = createOverwriteTransaction(position, length)

        this.position = position
    }

    /**
     * @param length if smaller 0 all remaining data is deleted
     */
    suspend fun delete(position: Long, length: Long) {
        if (length == 0L)
            return
        if (currentTransaction != null)
            this.position = currentTransaction!!.finish()
        // get the container length after finishing the previous transaction
        var containerLength = container.getDataLength()
        val deleteLength = if (length > 0) length else containerLength - position
        if (deleteLength == 0L)
            return
        if (position < 0 || position + deleteLength > containerLength)
            throw IllegalArgumentException("Arguments out of bounds")

        currentTransaction = createInsertTransaction(position, containerLength)
        (currentTransaction as InsertTransaction).delete(deleteLength)
        this.position = currentTransaction!!.finish()
        currentTransaction = null
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        if (currentTransaction == null)
            seek(position())
        currentTransaction!!.write(buffer, offset, length)
        return length
    }

    override suspend fun flush() {
        return flush(true)
    }

    suspend fun flush(flushContainer: Boolean) {
        if (currentTransaction != null) {
            position = currentTransaction!!.finish()
            currentTransaction = null
        }

        if (flushContainer) {
            container.flush(false)
        }
    }

    fun position(): Long {
        return currentTransaction?.position() ?: position
    }

    suspend fun truncate(size: Long) {
        delete(size, -1)
    }

    override suspend fun close() {
        flush()
        currentTransaction = null
    }
}
