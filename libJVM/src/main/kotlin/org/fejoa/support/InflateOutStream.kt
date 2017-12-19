package org.fejoa.support

import java.io.OutputStream
import java.util.zip.InflaterOutputStream


actual class InflateOutStream actual constructor(val outStream: OutStream) : OutStream {
    private val inflater = InflaterOutputStream(object : OutputStream() {
        override fun write(byte: Int) {
            outStream.writeByte(byte)
        }
    })

    override fun write(data: ByteArray, offset: Int, length: Int): Int {
        inflater.write(data, offset, length)
        return length
    }

    override fun write(byte: Byte): Int {
        inflater.write(byte.toInt())
        return 1
    }

    override fun flush() {
        inflater.flush()
    }

    override fun close() {
        inflater.close()
    }
}