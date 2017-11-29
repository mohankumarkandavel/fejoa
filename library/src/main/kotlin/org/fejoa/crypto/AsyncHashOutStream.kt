package org.fejoa.crypto

import org.fejoa.support.AsyncOutStream


interface AsyncHashOutStream : AsyncOutStream {
    suspend fun hash(): ByteArray
    fun reset()
}