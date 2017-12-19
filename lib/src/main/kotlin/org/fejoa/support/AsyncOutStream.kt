package org.fejoa.support


interface AsyncOutStream : AsyncCloseable {
    suspend fun write(buffer: ByteArray): Int {
        return write(buffer, 0, buffer.size)
    }

    suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int
    suspend fun flush() {}
}

suspend fun AsyncOutStream.write(byte: Int): Int {
    return this.writeByte(byte.toByte())
}

suspend fun AsyncOutStream.writeByte(value: Byte): Int {
    val buffer = ByteArray(1)
    buffer[0] = value
    this.write(buffer)
    return 1
}

suspend fun AsyncOutStream.writeShort(value: Short): Int {
    this.writeByte((value.toInt() shr 8 and 0xFF).toByte())
    this.writeByte((value.toInt() and 0xFF).toByte())
    return 2
}

suspend fun AsyncOutStream.writeInt(value: Int): Int {
    this.writeByte((value shr 24 and 0xFF).toByte())
    this.writeByte((value shr 16 and 0xFF).toByte())
    this.writeByte((value shr 8 and 0xFF).toByte())
    this.writeByte((value and 0xFF).toByte())
    return 4
}

suspend fun AsyncOutStream.writeLong(value: Long): Int {
    this.writeByte((value shr 56 and 0xFF).toByte())
    this.writeByte((value shr 48 and 0xFF).toByte())
    this.writeByte((value shr 40 and 0xFF).toByte())
    this.writeByte((value shr 32 and 0xFF).toByte())
    this.writeByte((value shr 24 and 0xFF).toByte())
    this.writeByte((value shr 16 and 0xFF).toByte())
    this.writeByte((value shr 8 and 0xFF).toByte())
    this.writeByte((value and 0xFF).toByte())
    return 8
}