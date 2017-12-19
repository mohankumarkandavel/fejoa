package org.fejoa.support


class ByteArrayInStream(val buffer: ByteArray, offset: Int = 0, val length: Int = buffer.size) : InStream {
    actual constructor(buffer: ByteArray) : this(buffer, 0, buffer.size)

    private var position = offset

    override fun read(): Int {
        if (position >= length)
            return -1
        val value = buffer[position]
        position++
        return value.toInt() and 0xFF
    }
}
