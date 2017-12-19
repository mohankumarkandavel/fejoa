package org.fejoa.support


class ByteArrayOutStream : OutStream {
    private var buffer = ByteArray(64)
    private var position = 0

    private fun ensureBufferSize(requiredSize: Int) {
        var newBufferSize = buffer.size
        while (newBufferSize < position + requiredSize)
            newBufferSize *= 2
        if (newBufferSize == buffer.size)
            return

        buffer = buffer.copyOf(newBufferSize)
    }

    override fun write(byte: Byte): Int {
        ensureBufferSize(1)
        buffer[position] = byte
        position++
        return 1
    }

    override fun write(data: ByteArray, offset: Int, length: Int): Int {
        ensureBufferSize(length)
        for (i in offset until offset + length) {
            buffer[position] = data[i]
            position++
        }
        return length
    }

    fun toByteArray(): ByteArray {
        return buffer.copyOf(position)
    }

    override fun flush() {

    }

    override fun close() {

    }
}