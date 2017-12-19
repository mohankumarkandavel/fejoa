package org.fejoa.support

import org.fejoa.crypto.AsyncHashOutStream


class AsyncHashOutStream(val out: AsyncOutStream, val hashOutStream: AsyncHashOutStream) : AsyncOutStream {
    suspend override fun flush() {
        out.flush()
    }

    suspend override fun close() {
        out.close()
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        hashOutStream.write(buffer, offset, length)
        return out.write(buffer, offset, length)
    }
}