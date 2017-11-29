package org.fejoa.support


interface AsyncInStream {
    suspend fun read(buffer: ByteArray): Int {
        return read(buffer, 0, buffer.size)
    }

    suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

suspend fun AsyncInStream.readFully(b: ByteArray) = this.readFully(b, 0, b.size)

suspend fun AsyncInStream.readFully(b: ByteArray, off: Int, len: Int) {
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

suspend fun AsyncInStream.readFully(length: Int): ByteArray {
    val buffer = ByteArray(length)
    readFully(buffer)
    return buffer
}

suspend fun AsyncInStream.copyTo(outputStream: OutStream) {
    val buffer = ByteArray(StreamHelper.BUFFER_SIZE)
    while (true) {
        val length = this.read(buffer)
        if (length <= 0)
            break
        outputStream.write(buffer, 0, length)
    }
}

suspend fun AsyncInStream.readAll(): ByteArray {
    val outputStream = ByteArrayOutStream()
    this.copyTo(outputStream)
    return outputStream.toByteArray()
}

suspend fun AsyncInStream.read(): Int {
    val buffer = ByteArray(1)
    if (this.read(buffer) != 1)
        return -1
    return buffer[0].toInt() and 0xFF
}

suspend fun AsyncInStream.readByte(): Byte {
    val buffer = ByteArray(1)
    if (this.read(buffer) != 1)
        throw EOFException()
    return buffer[0]
}

suspend fun AsyncInStream.readShort(): Short {
    val byte0 = this.read()
    val byte1 = this.read()
    if (byte0 < 0 || byte1 < 0)
        throw EOFException()
    return (byte0 shl 8 + byte1).toShort()
}

suspend fun AsyncInStream.readInt(): Int {
    val byte0 = this.read()
    val byte1 = this.read()
    val byte2 = this.read()
    val byte3 = this.read()
    if (byte0 < 0 || byte1 < 0 || byte2 < 0 || byte3 < 0)
        throw EOFException()
    return (byte0 shl 24) + (byte1 shl 16) + (byte2 shl 8) + byte3
}

suspend fun AsyncInStream.readLong(): Long {
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

fun InStream.toAsyncInputStream(): AsyncInStream {
    val that = this
    return object : AsyncInStream {
        suspend override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return that.read(buffer, offset, length)
        }
    }
}