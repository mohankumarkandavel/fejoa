package org.fejoa.support


class AsyncByteArrayOutStream : AsyncOutStream {
    private val output = ByteArrayOutStream()

    suspend override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        return output.write(buffer, offset, length)
    }

    suspend override fun flush() {
        output.flush()
    }

    suspend override fun close() {
        output.close()
    }

    fun toByteArray(): ByteArray {
        return output.toByteArray()
    }
}