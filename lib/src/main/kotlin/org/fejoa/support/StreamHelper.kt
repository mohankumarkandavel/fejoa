package org.fejoa.support

import org.fejoa.protocolbufferlight.VarInt
import kotlin.math.min


object StreamHelper {
    var BUFFER_SIZE = 8 * 1024

    fun copyBytes(inputStream: InStream, outputStream: OutStream, size: Int) {
        val bufferLength = BUFFER_SIZE
        val buf = ByteArray(bufferLength)
        var bytesRead = 0
        while (bytesRead < size) {
            val requestedBunchSize = min(size - bytesRead, bufferLength)
            val read = inputStream.read(buf, 0, requestedBunchSize)
            bytesRead += read
            outputStream.write(buf, 0, read)
        }
    }

    fun copy(inputStream: InStream, outputStream: OutStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val length = inputStream.read(buffer)
            if (length <= 0)
                break
            outputStream.write(buffer, 0, length)
        }
    }

    fun readAll(inputStream: InStream): ByteArray {
        val outputStream = ByteArrayOutStream()
        copy(inputStream, outputStream)
        return outputStream.toByteArray()
    }

    fun readString0(inputStream: InStream): String {
        var c = inputStream.read()
        val builder = StringBuilder("")
        while (c != -1 && c != 0) {
            builder.append(c.toChar())
            c = inputStream.read()
        }
        return builder.toString()
    }

    /**
     * Writes a data package.
     *
     * The length of the data is prepend to the data.
     */
    suspend fun writeDataPackage(outStream: AsyncOutStream, data: ByteArray) {
        VarInt.write(outStream, data.size)
        outStream.write(data)
    }

    suspend fun readDataPackage(inStream: AsyncInStream, maxLength: Int): ByteArray {
        val length = VarInt.read(inStream).first.toInt()
        if (length > maxLength)
            throw IOException("String is too long: " + length)
        val buffer = ByteArray(length)
        inStream.readFully(buffer)
        return buffer
    }

    suspend fun readString(inStream: AsyncInStream, maxLength: Int): String {
        return readDataPackage(inStream, maxLength).toUTFString()
    }

    suspend fun writeString(outStream: AsyncOutStream, string: String) {
        writeDataPackage(outStream, string.toUTF())
    }
}
