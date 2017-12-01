package org.fejoa.chunkcontainer

import org.fejoa.storage.HashValue

import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.support.InStream


interface Chunk {
    suspend fun hash(hashOutStream: AsyncHashOutStream): HashValue
    suspend fun read(inputStream: InStream, dataLength: Long)

    suspend fun getData(): ByteArray
    fun getDataLength(): Long
}
