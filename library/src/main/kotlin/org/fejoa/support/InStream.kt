package org.fejoa.support


interface InStream {
    fun read(): Int

    fun read(buffer: ByteArray): Int {
        return read(buffer, 0, buffer.size)
    }

    /**
     * @return the number of bytes read
     */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var bytesRead = 0
        for (i in offset until offset + length) {
            val byte = read()
            if (byte < 0)
                return bytesRead
            bytesRead++
            buffer[i] = byte.toByte()
        }
        return bytesRead
    }
}

fun InStream.readFully(b: ByteArray) = this.readFully(b, 0, b.size)

fun InStream.readFully(b: ByteArray, off: Int, len: Int) {
    if (len < 0 || len > off + b.size)
        throw IllegalArgumentException()
    var bytesRead = 0
    while (bytesRead != len) {
        val read = this.read(b, off + bytesRead, len - bytesRead)
        if (read < 0)
            throw EOFException()
        bytesRead += read
    }
}

fun InStream.readFully(length: Int): ByteArray {
    val buffer = ByteArray(length)
    readFully(buffer)
    return buffer
}

fun InStream.copyTo(outputStream: OutStream) {
    val buffer = ByteArray(StreamHelper.BUFFER_SIZE)
    while (true) {
        val length = this.read(buffer)
        if (length <= 0)
            break
        outputStream.write(buffer, 0, length)
    }
}

fun InStream.readAll(): ByteArray {
    val outputStream = ByteArrayOutStream()
    this.copyTo(outputStream)
    return outputStream.toByteArray()
}

fun InStream.read(): Int {
    val buffer = ByteArray(1)
    if (this.read(buffer) != 1)
        return -1
    return buffer[0].toInt() and 0xFF
}

fun InStream.readByte(): Byte {
    val buffer = ByteArray(1)
    if (this.read(buffer) != 1)
        throw EOFException()
    return buffer[0]
}

fun InStream.readShort(): Short {
    val byte0 = this.read()
    val byte1 = this.read()
    if (byte0 < 0 || byte1 < 0)
        throw EOFException()
    return (byte0 shl 8 + byte1).toShort()
}

fun InStream.readInt(): Int {
    val byte0 = this.read()
    val byte1 = this.read()
    val byte2 = this.read()
    val byte3 = this.read()
    if (byte0 < 0 || byte1 < 0 || byte2 < 0 || byte3 < 0)
        throw EOFException()
    return (byte0 shl 24) + (byte1 shl 16) + (byte2 shl 8) + byte3
}

fun InStream.readLong(): Long {
    val byte0 = this.read().toLong()
    val byte1 = this.read().toLong()
    val byte2 = this.read().toLong()
    val byte3 = this.read().toLong()
    val byte4 = this.read().toLong()
    val byte5 = this.read().toLong()
    val byte6 = this.read().toLong()
    val byte7 = this.read().toLong()

    if (byte0 < 0 || byte1 < 0 || byte2 < 0 || byte3 < 0 || byte4 < 0 || byte5 < 0 || byte6 < 0 || byte7 < 0)
        throw EOFException()

    return (byte0 shl 56) + (byte1 shl 48) + (byte2 shl 40) + (byte3 shl 32) + (byte4 shl 24) + (byte5 shl 16) + (byte6 shl 8) + byte7
}
