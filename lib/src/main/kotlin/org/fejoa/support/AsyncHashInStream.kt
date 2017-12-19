package org.fejoa.support

import org.fejoa.crypto.AsyncHashOutStream


class AsyncHashInStream(val `in`: AsyncInStream, val hashOutStream: AsyncHashOutStream) : AsyncInStream {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = `in`.read(buffer, offset, length)
        hashOutStream.write(buffer, offset, read)
        return read
    }
}