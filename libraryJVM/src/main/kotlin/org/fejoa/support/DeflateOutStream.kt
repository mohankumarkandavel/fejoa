package org.fejoa.support

import java.io.OutputStream
import java.util.zip.DeflaterOutputStream


actual class DeflateOutStream actual constructor(val outStream: OutStream) : OutStream {
    private val deflater = DeflaterOutputStream(object : OutputStream() {
        override fun write(byte: Int) {
            outStream.writeByte(byte)
        }
    })

    override fun write(data: ByteArray, offset: Int, length: Int): Int {
        deflater.write(data, offset, length)
        return length
    }

    override fun write(byte: Byte): Int {
        deflater.write(byte.toInt())
        return 1
    }

    override fun flush() {
        deflater.flush()
    }

    override fun close() {
        deflater.close()
    }
}