package org.fejoa.crypto

import java.security.MessageDigest


open class JVMAsyncHashOutStream(private val messageDigest: MessageDigest) : AsyncHashOutStream {
    suspend override fun hash(): ByteArray {
        return messageDigest!!.digest()
    }

    override fun reset() {
        messageDigest!!.reset()
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): Int {
        messageDigest.update(data, offset, length)
        return length
    }

    override suspend fun flush() {

    }

    override suspend fun close() {
    }
}