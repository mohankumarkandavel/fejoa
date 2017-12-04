package org.fejoa.chunkcontainer

import org.fejoa.repository.readVarIntDelimited
import org.fejoa.repository.writeVarIntDelimited
import org.fejoa.support.*


val MOST_SIGNIFICANT_BIT = 1 shl 7

data class BoxSpec(val encInfo: EncryptionInfo = EncryptionInfo(),
              val zipType: ZipType = ZipType.DEFLATE, val zipBeforeEnc: Boolean = true,
              val nodeNormalization: Boolean = false,
              val dataNormalization: Boolean = false) {
    fun clone(): BoxSpec {
        return BoxSpec(encInfo.clone(), zipType)
    }

    /**
     * Format:
     * |! EncType  (1|      -> bitfield: |extension [1| reserved [4|enc type [3|
     * {enc data}
     */
    data class EncryptionInfo(val type: EncryptionInfo.Type, val data: ByteArray) {
        constructor() : this(Type.PARENT, ByteArray(0))
        constructor(type: EncryptionInfo.Type) : this(type, ByteArray(0))

        fun clone(): EncryptionInfo {
            return EncryptionInfo(type, data.copyOf())
        }

        enum class Type(val value: Int) {
            PLAIN(0),
            PARENT(1), // encryption key and parameters are derived from the repo config
            //CUSTOM(2),
        }

        companion object {
            private val ENC_TYPE_MASK = 0x7

            suspend fun read(inStream: AsyncInStream): EncryptionInfo {
                val rawByte = inStream.readByte().toInt()
                val typeValue = rawByte and ENC_TYPE_MASK
                val type = EncryptionInfo.Type.values().firstOrNull { it.value == typeValue }
                        ?: throw IOException("Unknown type $typeValue")
                val data = if (rawByte and MOST_SIGNIFICANT_BIT != 0) inStream.readVarIntDelimited().first
                else ByteArray(0)
                return EncryptionInfo(type, data)
            }
        }

        suspend fun write(outStream: AsyncOutStream): Int {
            var bytesWritten = outStream.writeByte(type.value.toByte())
            if (type.value and MOST_SIGNIFICANT_BIT != 0)
                bytesWritten += outStream.writeVarIntDelimited(data)
            return bytesWritten
        }

    }

    // |EncryptionInfo|
    // |! Config (1| -> |extension [1|reserved [1|data norm [1|node norm [1|zip order [1|ZipType [3|
    //                  (zip order indicates if compression is applied before (1) or after (0) encryption
    // {extension (optional} (indicated through MSB in Config; not implemented)
    companion object {
        private val ZIP_TYPE_MASK = 0x7
        private val ZIP_ORDER_MASK = 1 shr 3
        private val NODE_NORM_MASK = 1 shr 4
        private val DATA_NORM_MASK = 1 shr 5

        suspend fun read(inStream: AsyncInStream): BoxSpec {
            val encInfo = EncryptionInfo.read(inStream)
            val config = inStream.readByte().toInt()

            if (config and MOST_SIGNIFICANT_BIT != 0)
                inStream.readVarIntDelimited() // read extension but ignore it for now

            val zipTypeValue = config and ZIP_TYPE_MASK
            val zipType = BoxSpec.ZipType.values().firstOrNull { it.value == zipTypeValue }
                    ?: throw IOException("Unknown type")
            val zipBeforeEnc = config and ZIP_ORDER_MASK != 0
            val nodeNormalization = config and NODE_NORM_MASK != 0
            val dataNormalization = config and DATA_NORM_MASK != 0

            return BoxSpec(encInfo, zipType, zipBeforeEnc = zipBeforeEnc,
                    nodeNormalization = nodeNormalization, dataNormalization = dataNormalization)
        }
    }

    suspend fun write(outStream: AsyncOutStream): Int {
        var bytesWritten = encInfo.write(outStream)
        var config = zipType.value and ZIP_TYPE_MASK
        if (zipBeforeEnc)
            config = config or ZIP_ORDER_MASK
        if (nodeNormalization)
            config = config or NODE_NORM_MASK
        if (dataNormalization)
            config = config or DATA_NORM_MASK
        
        bytesWritten += outStream.write(config)
        return bytesWritten
    }

    enum class ZipType(val value: Int) {
        NONE(0),
        DEFLATE(1)
    }
}