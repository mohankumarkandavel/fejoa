package org.fejoa.chunkcontainer

import org.fejoa.storage.RandomDataAccess
import org.fejoa.support.IOException

import kotlin.math.max


class ChunkContainerRandomDataAccess(private var chunkContainer: ChunkContainer,
                                     val mode: RandomDataAccess.Mode,
                                     private val callback: IOCallback = IOCallback(),
                                     val normalizeChunkSize: Boolean = chunkContainer.ref.boxSpec.dataNormalization)
    : RandomDataAccess {
    private var isOpen = true
    private var position: Long = 0
    private var inputStream: ChunkContainerInStream? = null
    private var outputStream: ChunkContainerOutStream? = null

    open class IOCallback {
        open suspend fun requestRead(caller: ChunkContainerRandomDataAccess) {}
        open suspend fun requestWrite(caller: ChunkContainerRandomDataAccess) {}
        open fun onClosed(caller: ChunkContainerRandomDataAccess) {}
    }

    fun getChunkContainer(): ChunkContainer {
        return chunkContainer
    }

    fun cancel() {
        position = -1
        inputStream = null
        outputStream = null
    }

    private fun checkNotCanceled() {
        if (position < 0)
            throw IOException("Access has been canceled")
    }

    suspend private fun prepareForWrite() {
        if (!mode.has(RandomDataAccess.Mode.WRITE))
            throw IOException("Write permission is missing")

        checkNotCanceled()

        if (inputStream != null) {
            inputStream = null
        }
        if (outputStream == null) {
            outputStream = ChunkContainerOutStream(chunkContainer, mode, normalizeChunkSize = normalizeChunkSize)
            outputStream!!.seek(position)
        }

        callback.requestWrite(this)
    }

    override fun mode(): RandomDataAccess.Mode {
        return mode
    }

    suspend private fun prepareForRead() {
        if (!mode.has(RandomDataAccess.Mode.READ))
            throw IOException("Read permission is missing")

        checkNotCanceled()

        if (outputStream != null) {
            outputStream!!.close()
            outputStream = null
        }
        if (inputStream == null) {
            inputStream = ChunkContainerInStream(chunkContainer)
            inputStream!!.seek(position)
        }

        callback.requestRead(this)
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    override fun length(): Long {
        return max(chunkContainer.getDataLength(), position)
    }

    override fun position(): Long {
        return position
    }

    override suspend fun seek(position: Long) {
        this.position = position
        if (inputStream != null)
            inputStream!!.seek(position)
        else if (outputStream != null)
            outputStream!!.seek(position)
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): Int {
        prepareForWrite()
        outputStream!!.write(data, offset, length)
        position += length.toLong()
        return length
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        prepareForRead()
        val read = inputStream!!.read(buffer, offset, length)
        position += read.toLong()
        return read
    }

    override suspend fun flush() {
        if (!mode.has(RandomDataAccess.Mode.WRITE))
            throw IOException("Can't flush in read only mode.")
        if (outputStream != null)
            outputStream!!.flush()
    }

    override suspend fun truncate(size: Long) {
        prepareForWrite()
        outputStream!!.truncate(size)
    }

    override suspend fun delete(position: Long, length: Long) {
        prepareForWrite()
        outputStream!!.delete(position, length)
    }

    override suspend fun close() {
        isOpen = false
        if (inputStream != null) {
            inputStream = null
        } else if (outputStream != null) {
            outputStream!!.close()
            outputStream = null
        }
        callback.onClosed(this)
    }
}
