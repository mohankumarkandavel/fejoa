package org.fejoa.support


interface OutStream {
    fun write(byte: Byte): Int
    fun write(data: ByteArray, offset: Int, length: Int): Int {
        for (i in offset until length)
            write(data[i])
        return length
    }
    fun write(data: ByteArray): Int {
        return write(data, 0, data.size)
    }
    fun flush() {}
    fun close() {}
}


fun OutStream.writeByte(byte: Int): Int {
    return write((byte and 0xFF).toByte())
}

fun OutStream.writeShort(value: Short): Int {
    this.writeByte(value.toInt() shr 8)
    this.writeByte(value.toInt())
    return 2
}

fun OutStream.writeInt(value: Int): Int {
    this.writeByte(value shr 24)
    this.writeByte(value shr 16)
    this.writeByte(value shr 8)
    this.writeByte(value)
    return 4
}

fun OutStream.writeLong(value: Long): Int {
    this.write((value shr 56 and 0xFF).toByte())
    this.write((value shr 48 and 0xFF).toByte())
    this.write((value shr 40 and 0xFF).toByte())
    this.write((value shr 32 and 0xFF).toByte())
    this.write((value shr 24 and 0xFF).toByte())
    this.write((value shr 16 and 0xFF).toByte())
    this.write((value shr 8 and 0xFF).toByte())
    this.write((value and 0xFF).toByte())
    return 8
}
