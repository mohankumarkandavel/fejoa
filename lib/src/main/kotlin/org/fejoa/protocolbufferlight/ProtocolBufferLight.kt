package org.fejoa.protocolbufferlight

import org.fejoa.support.AsyncOutStream
import org.fejoa.support.AsyncInStream
import org.fejoa.support.readFully
import org.fejoa.support.*


/* used for chunking, container and encryption details
Loosely based on: https://developers.google.com/protocol-buffers/docs/encoding#types
but no need for an extra compiler.
 */
class ProtocolBufferLight {
    internal enum class DataType constructor(val value: Int) {
        VAR_INT(0),
        SIZE_64(1),
        LENGTH_DELIMITED(2),
        START_GROUP(3),
        END_GROUP(4),
        SIZE_32(5)
    }

    internal class Key(val type: DataType, val tag: Long)


    internal interface IValue {
        fun write(outputStream: OutStream)
        fun read(inStream: InStream)

        suspend fun write(outStream: AsyncOutStream)
        suspend fun read(inStream: AsyncInStream)
    }

    internal class KeyValue(val key: Key, val value: IValue)

    internal class VarIntValue : IValue {
        var number: Long = 0
            private set

        private constructor()

        companion object {
            suspend fun read(inStream: AsyncInStream): VarIntValue {
                val value = VarIntValue()
                value.read(inStream)
                return value
            }
        }

        constructor(inStream: InStream) {
            read(inStream)
        }

        constructor(number: Long) {
            this.number = number
        }

        override fun write(outputStream: OutStream) {
            VarInt.write(outputStream, number)
        }

        override fun read(inStream: InStream) {
            this.number = VarInt.read(inStream).first
        }

        suspend override fun write(outStream: AsyncOutStream) {
            VarInt.write(outStream, number)
        }

        suspend override fun read(inStream: AsyncInStream) {
            this.number = VarInt.read(inStream).first
        }
    }

    internal class ByteValue : IValue {
        var bytes: ByteArray? = null
            private set

        constructor(inStream: InStream) {
            read(inStream)
        }

        private constructor()

        companion object {
            suspend fun read(inStream: AsyncInStream): ByteValue {
                val value = ByteValue()
                value.read(inStream)
                return value
            }
        }

        constructor(bytes: ByteArray) {
            this.bytes = bytes
        }

        override fun write(outputStream: OutStream) {
            VarInt.write(outputStream, bytes!!.size)
            outputStream.write(bytes!!)
        }

        override fun read(inStream: InStream) {
            val length = VarInt.read(inStream).first.toInt()
            this.bytes = ByteArray(length)
            inStream.readFully(bytes!!)
        }

        suspend override fun write(outStream: AsyncOutStream) {
            VarInt.write(outStream, bytes!!.size)
            outStream.write(bytes!!)
        }

        suspend override fun read(inStream: AsyncInStream) {
            val length = VarInt.read(inStream).first.toInt()
            this.bytes = ByteArray(length)
            inStream.readFully(bytes!!)
        }
    }

    private val map: MutableMap<Int, KeyValue> = HashMap()

    private val DATA_TYPE_MASK: Long = 0x7
    private val TAG_SHIFT: Int = 3

    constructor()

    constructor(bytes: ByteArray) {
        read(ByteArrayInStream(bytes))
    }

    companion object {
        suspend fun read(inStream: AsyncInStream): ProtocolBufferLight {
            val buffer = ProtocolBufferLight()
            buffer.read(inStream)
            return buffer
        }
    }

    /**
     * Try to read a Key
     *
     * @return null if the end of stream is reached
     */
    private fun readKey(inStream: InStream): Key? {
        val number = try {
            VarInt.read(inStream).first
        } catch (e: EOFException) {
            return null
        }
        val dataType = (number and DATA_TYPE_MASK).toInt()
        val tag = number shr TAG_SHIFT
        if (dataType == DataType.VAR_INT.value)
            return Key(DataType.VAR_INT, tag)
        if (dataType == DataType.LENGTH_DELIMITED.value)
            return Key(DataType.LENGTH_DELIMITED, tag)

        throw IOException("Unknown data type: " + dataType)
    }

    suspend private fun readKey(inStream: AsyncInStream): Key? {
        val number = try {
            VarInt.read(inStream).first
        } catch (e: EOFException) {
            return null
        }
        val dataType = (number and DATA_TYPE_MASK).toInt()
        val tag = number shr TAG_SHIFT
        if (dataType == DataType.VAR_INT.value)
            return Key(DataType.VAR_INT, tag)
        if (dataType == DataType.LENGTH_DELIMITED.value)
            return Key(DataType.LENGTH_DELIMITED, tag)

        throw IOException("Unknown data type: " + dataType)
    }

    fun clear() {
        map.clear()
    }

    fun put(tag: Int, bytes: ByteArray) {
        map.put(tag, KeyValue(Key(DataType.LENGTH_DELIMITED, tag.toLong()), ByteValue(bytes)))
    }

    fun put(tag: Int, string: String) {
        put(tag, string.toUTF())
    }

    fun put(tag: Int, value: Long) {
        map.put(tag, KeyValue(Key(DataType.VAR_INT, tag.toLong()), VarIntValue(value)))
    }

    fun put(tag: Int, value: Int) {
        map.put(tag, KeyValue(Key(DataType.VAR_INT, tag.toLong()), VarIntValue(value.toLong())))
    }

    fun getBytes(tag: Int): ByteArray? {
        val keyValue = map[tag] ?: return null
        assert(keyValue.key.tag == tag.toLong())

        return if (keyValue.key.type != DataType.LENGTH_DELIMITED) null else (keyValue.value as ByteValue).bytes
    }

    fun getString(tag: Int): String? {
        val bytes = getBytes(tag)
        return if (bytes == null) null else bytes.toUTFString()
    }

    fun getLong(tag: Int): Long? {
        val keyValue = map[tag] ?: return null
        assert(keyValue.key.tag == tag.toLong())

        return if (keyValue.key.type != DataType.VAR_INT) null else (keyValue.value as VarIntValue).number
    }

    private fun writeKey(outputStream: OutStream, key: Key) {
        var outValue = key.tag shl TAG_SHIFT
        outValue = outValue or key.type.value.toLong()
        VarInt.write(outputStream, outValue)
    }

    suspend private fun writeKey(outStream: AsyncOutStream, key: Key) {
        var outValue = key.tag shl TAG_SHIFT
        outValue = outValue or key.type.value.toLong()
        VarInt.write(outStream, outValue)
    }

    fun toByteArray(): ByteArray {
        val outputStream = ByteArrayOutStream()
        write(outputStream)
        return outputStream.toByteArray()
    }

    fun write(outputStream: OutStream) {
        for ((_, keyValue) in map) {
            writeKey(outputStream, keyValue.key)
            keyValue.value.write(outputStream)
        }
    }

    suspend fun write(outStream: AsyncOutStream) {
        for ((_, keyValue) in map) {
            writeKey(outStream, keyValue.key)
            keyValue.value.write(outStream)
        }
    }

    private fun readKeyValue(inStream: InStream): KeyValue? {
        val key = readKey(inStream) ?: return null
        if (key.type == DataType.VAR_INT) {
            val varIntValue = VarIntValue(inStream)
            return KeyValue(key, varIntValue)
        }
        if (key.type == DataType.LENGTH_DELIMITED) {
            val byteValue = ByteValue(inStream)
            return KeyValue(key, byteValue)
        }
        throw IOException("Unknown data type: " + key.type.value)
    }

    suspend private fun readKeyValue(inStream: AsyncInStream): KeyValue? {
        val key = readKey(inStream) ?: return null
        if (key.type == DataType.VAR_INT) {
            val varIntValue = VarIntValue.read(inStream)
            return KeyValue(key, varIntValue)
        }
        if (key.type == DataType.LENGTH_DELIMITED) {
            val byteValue = ByteValue.read(inStream)
            return KeyValue(key, byteValue)
        }
        throw IOException("Unknown data type: " + key.type.value)
    }

    /**
     * Read key value pairs till the end of stream is reached.
     *
     * @param InStream
     */
    private fun read(inStream: InStream) {
        map.clear()
        while (true) {
            // read till end of file
            val keyValue = readKeyValue(inStream) ?: break
            map.put(keyValue.key.tag.toInt(), keyValue)
        }
    }

    private suspend fun read(inStream: AsyncInStream) {
        map.clear()
        while (true) {
            val keyValue = readKeyValue(inStream) ?: break
            map.put(keyValue.key.tag.toInt(), keyValue)
        }
    }
}
