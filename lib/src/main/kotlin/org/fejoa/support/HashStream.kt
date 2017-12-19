package org.fejoa.support

interface HashStream : OutStream {
    fun hash(): ByteArray
}