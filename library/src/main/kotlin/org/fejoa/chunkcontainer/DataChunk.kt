package org.fejoa.chunkcontainer

import org.fejoa.support.StreamHelper
import org.fejoa.crypto.CryptoHelper
import org.fejoa.storage.HashValue

import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.support.ByteArrayOutStream
import org.fejoa.support.InStream


class DataChunk(private var data: ByteArray, private var dataLength: Int = data.size) : Chunk {
    constructor() : this(ByteArray(0), 0)

    override fun getData(): ByteArray {
        if (data.size == dataLength)
            return data
        return data.copyOfRange(0, dataLength)
    }

    override fun getDataLength(): Long {
        return dataLength.toLong()
    }

    override suspend fun hash(hashOutStream: AsyncHashOutStream): HashValue {
        return HashValue(CryptoHelper.hash(data, 0, dataLength, hashOutStream))
    }

    suspend override fun read(inputStream: InStream, dataLength: Long) {
        val outputStream = ByteArrayOutStream()
        StreamHelper.copy(inputStream, outputStream)
        this.data = outputStream.toByteArray()
        this.dataLength = dataLength.toInt()
    }
}
