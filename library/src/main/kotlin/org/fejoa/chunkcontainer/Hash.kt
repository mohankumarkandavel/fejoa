package org.fejoa.chunkcontainer

import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.repository.readVarIntDelimited
import org.fejoa.repository.writeVarIntDelimited
import org.fejoa.chunkcontainer.HashSpec.HashType.*
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.HashOutStreamFactory
import org.fejoa.crypto.SHA256Factory
import org.fejoa.storage.*
import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.support.*


/**
 * |HashSpec|
 * |hash (32|
 */
class Hash(val spec: HashSpec, var value: HashValue) {
    constructor() : this(HashSpec(), Config.newDataHash())
    constructor(hashSpec: HashSpec) : this(hashSpec, Config.newDataHash())

    fun clone(): Hash = Hash(spec.clone(), value.clone())

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Hash)
            return false
        return value == other.value
    }

    companion object {
        suspend fun read(inStream: AsyncInStream): Hash {
            val info = HashSpec.read(inStream)
            val hash = Config.newDataHash()
            inStream.read(hash.bytes)
            return Hash(info, hash)
        }
    }

    suspend fun write(outStream: AsyncOutStream): Int {
        var bytesWritten = spec.write(outStream)
        outStream.write(value.bytes)
        bytesWritten += value.bytes.size
        return bytesWritten
    }
}


/**
 * |!HashType (1 byte)| -> bit field: |ext [1|compact [1|HashType [6|
 * {Hash Type info (optional)}
 */
class HashSpec(chunkingConfig: ChunkingConfig) {
    constructor() : this(RabinChunkingConfig.create(DEFAULT))
    constructor(type: HashType) : this(getDefaultChunkingConfig(type))

    fun clone(): HashSpec {
        return HashSpec(chunkingConfig.clone())
    }

    enum class HashType(val value: Int) {
        SHA_256(0),
        // chunking hashes:
        FEJOA_FIXED_CUSTOM(((CC_HASH_OFFSET + 1) or CUSTOM_HASH_MASK)),
        FEJOA_FIXED_8K(CC_HASH_OFFSET + 2),
        FEJOA_RABIN_CUSTOM((CC_HASH_OFFSET + 11) or CUSTOM_HASH_MASK),
        FEJOA_RABIN_2KB_8KB(CC_HASH_OFFSET + 12),
        FEJOA_RABIN_2KB_8KB_COMPACT(FEJOA_RABIN_2KB_8KB.value or COMPACT_MASK)
    }

    var chunkingConfig: ChunkingConfig = chunkingConfig
        private set
    val compact: Boolean
        get() = chunkingConfig.isCompact
    val type: HashType
        get() = chunkingConfig.chunkingType

    companion object {
        val DEFAULT = FEJOA_RABIN_2KB_8KB

        val COMPACT_MASK = 1 shl 6
        val CUSTOM_HASH_MASK = 1 shl 7
        val HASH_TYPE_MASK = 0xFF
        val CC_HASH_OFFSET = 10

        suspend fun read(inStream: AsyncInStream): HashSpec {
            val rawByte = inStream.read()

            val typeValue = rawByte and HASH_TYPE_MASK
            val type = HashType.values().firstOrNull { it.value == typeValue }
                    ?: throw IOException("Unknown HashType $typeValue")

            val extra = if (type.value and CUSTOM_HASH_MASK != 0) inStream.readVarIntDelimited().first
                                    else ByteArray(0)
            return HashSpec(getChunkingConfig(type, extra))
        }

        internal enum class RabinDetailTag(val value: Int) {
            TARGET_CHUNK_SIZE(0),
            MIN_CHUNK_SIZE(1),
            MAX_CHUNK_SIZE(2)
        }

        internal enum class FixedSizeDetailTag(val value: Int) {
            SIZE(0)
        }

        private fun getDefaultChunkingConfig(type: HashType): ChunkingConfig {
            return when (type) {
                SHA_256 -> NonChunkingConfig(SHA_256)
                FEJOA_FIXED_8K -> FixedSizeChunkingConfig.create(type)
                FEJOA_FIXED_CUSTOM -> throw Exception("Not allowed")
                FEJOA_RABIN_CUSTOM -> throw Exception("Not allowed")
                FEJOA_RABIN_2KB_8KB,
                FEJOA_RABIN_2KB_8KB_COMPACT -> RabinChunkingConfig.create(type)
            }
        }

        suspend private fun getChunkingConfig(type: HashType, extra: ByteArray): ChunkingConfig {
            val custom = type.value and CUSTOM_HASH_MASK != 0
            return when (type) {
                SHA_256 -> NonChunkingConfig(SHA_256)
                FEJOA_FIXED_8K,
                FEJOA_FIXED_CUSTOM -> {
                    val default = FixedSizeChunkingConfig.create(type)
                    if (custom)
                        readDetails(default, extra)
                    return default
                }
                FEJOA_RABIN_CUSTOM,
                FEJOA_RABIN_2KB_8KB,
                FEJOA_RABIN_2KB_8KB_COMPACT -> {
                    val default = RabinChunkingConfig.create(type)
                    if (custom)
                        readDetails(default, extra)
                    return default
                }
            }
        }

        suspend private fun readDetails(config: FixedSizeChunkingConfig, extra: ByteArray) {
            val inputStream = ByteArrayInStream(extra)
            val buffer = ProtocolBufferLight()
            buffer.read(inputStream)
            val value = buffer.getLong(FixedSizeDetailTag.SIZE.value)
            if (value != null)
                config.size = value.toInt()
        }

        suspend private fun readDetails(config: RabinChunkingConfig, extra: ByteArray) {
            val inputStream = ByteArrayInStream(extra)
            val buffer = ProtocolBufferLight()
            buffer.read(inputStream)
            var value = buffer.getLong(RabinDetailTag.TARGET_CHUNK_SIZE.value)
            if (value != null)
                config.targetSize = value.toInt()
            value = buffer.getLong(RabinDetailTag.MIN_CHUNK_SIZE.value)
            if (value != null)
                config.minSize = value.toInt()
            value = buffer.getLong(RabinDetailTag.MAX_CHUNK_SIZE.value)
            if (value != null)
                config.maxSize = value.toInt()
        }
    }

    suspend fun write(outStream: AsyncOutStream): Int {
        var rawByte = type.value and HASH_TYPE_MASK
        var bytesWritten = outStream.writeByte(rawByte.toByte())
        if (type.value and CUSTOM_HASH_MASK != 0)
            bytesWritten += outStream.writeVarIntDelimited(chunkingConfig.toByteArray())
        return bytesWritten
    }

    fun getHashOutStream(): AsyncHashOutStream {
        return when(this.type) {
            SHA_256 -> SHA256Factory().create()
            FEJOA_FIXED_CUSTOM,
            FEJOA_FIXED_8K,
            FEJOA_RABIN_CUSTOM,
            FEJOA_RABIN_2KB_8KB,
            FEJOA_RABIN_2KB_8KB_COMPACT-> ChunkHash(this)
        }
    }

    fun getBaseHashFactory(): HashOutStreamFactory = when (type) {
        SHA_256,
        FEJOA_FIXED_CUSTOM,
        FEJOA_FIXED_8K,
        FEJOA_RABIN_CUSTOM,
        FEJOA_RABIN_2KB_8KB,
        FEJOA_RABIN_2KB_8KB_COMPACT-> object : HashOutStreamFactory {
            override fun create(): AsyncHashOutStream = CryptoHelper.sha256Hash()
        }
    }

    fun getBaseHashOutStream(): AsyncHashOutStream = getBaseHashFactory().create()

    class DefaultNodeSplitter(val config: ChunkingConfig, val compact: Boolean) : NodeSplitterFactory {
        override fun nodeSizeFactor(level: Int): Float {
            if (level < 0)
                throw IllegalArgumentException()
            return when (level) {
                ChunkHash.DATA_LEVEL -> 1f
                else -> {
                    if (compact)
                        ChunkContainer.compactNodeSplittingRatio
                    else
                        ChunkContainer.nodeSplittingRatio
                }
            }
        }

        override fun create(level: Int): ChunkSplitter {
            return config.getSplitter(nodeSizeFactor(level))
        }
    }

    fun getNodeSplitterFactory(): NodeSplitterFactory {
        return chunkingConfig.getNodeSplitterFactory()
    }

    fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
        return chunkingConfig.getNodeWriteStrategy(normalizeChunkSize)
    }

    interface ChunkingConfig {
        fun getNodeSplitterFactory(): NodeSplitterFactory {
            return DefaultNodeSplitter(this, isCompact)
        }
        /**
         * Creates a splitter
         *
         * @param factor of how much the chunks should be smaller than in the config (see ChunkContainerNode).
         * @return
         */
        fun getSplitter(factor: Float): ChunkSplitter
        fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy
        fun toByteArray(): ByteArray
        fun clone(): ChunkingConfig
        val chunkingType: HashType
        val isCompact: Boolean
        val isDefault: Boolean
    }

    class NonChunkingConfig (chunkingType: HashType): ChunkingConfig {
        override fun getSplitter(factor: Float): ChunkSplitter {
            throw Exception("Not a chunking hash")
        }

        override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
            throw Exception("Not a chunking hash")
        }

        override fun toByteArray(): ByteArray {
            return ByteArray(0)
        }

        override fun clone(): ChunkingConfig {
            return NonChunkingConfig(chunkingType)
        }

        override val chunkingType: HashType = chunkingType
        override val isDefault: Boolean = false
        override val isCompact: Boolean = false
    }

    fun setRabinChunking(type: HashType) {
        chunkingConfig = RabinChunkingConfig.create(type)
    }

    fun setRabinChunking(type: HashType, targetSize: Int, minSize: Int, maxSize: Int) {
        val config = RabinChunkingConfig.create(type)
        chunkingConfig = config
        config.targetSize = targetSize
        config.minSize = minSize
        config.maxSize = maxSize
    }

    fun setRabinChunking(targetSize: Int, minSize: Int) {
        val config = RabinChunkingConfig.create(FEJOA_RABIN_CUSTOM)
        chunkingConfig = config
        config.targetSize = targetSize
        config.minSize = minSize
    }

    fun setFixedSizeChunking(size: Int) {
        val config = FixedSizeChunkingConfig.create(FEJOA_FIXED_CUSTOM)
        chunkingConfig = config
        config.size = size
    }

    class FixedSizeChunkingConfig : ChunkingConfig {
        override fun getSplitter(factor: Float): ChunkSplitter {
            return FixedBlockSplitter((factor * size).toInt())
        }

        override var chunkingType = FEJOA_FIXED_CUSTOM
            private set
        internal var size: Int = 0
        internal var defaultConfig: FixedSizeChunkingConfig? = null

        private constructor(type: HashType, size: Int) {
            this.chunkingType = type
            this.size = size
        }

        override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
            return FixedSizeNodeWriteStrategy(size, getNodeSplitterFactory(), 0, normalizeChunkSize)
        }

        override val isCompact: Boolean
            get() = chunkingType.value and COMPACT_MASK != 0

        override fun toByteArray(): ByteArray {
            val buffer = ProtocolBufferLight()
            if (size != defaultConfig!!.size)
                buffer.put(FixedSizeDetailTag.SIZE.value, size)
            val outputStream = ByteArrayOutStream()
            buffer.write(outputStream)
            return outputStream.toByteArray()
        }

        private constructor(type: HashType, defaultConfig: FixedSizeChunkingConfig?, size: Int) {
            this.chunkingType = type
            this.defaultConfig = defaultConfig
            this.size = size
        }

        override fun clone(): ChunkingConfig {
            return FixedSizeChunkingConfig(chunkingType, defaultConfig, size)
        }

        override val isDefault: Boolean
            get() = false

        companion object {
            fun create(type: HashType): FixedSizeChunkingConfig {
                val config = getDefault(type)
                config.defaultConfig = getDefault(type)
                return config
            }

            private fun getDefault(type: HashType): FixedSizeChunkingConfig {
                return when (type) {
                    FEJOA_FIXED_CUSTOM -> getDefault(FEJOA_FIXED_8K).also { it.chunkingType = FEJOA_FIXED_CUSTOM }
                    FEJOA_FIXED_8K -> FixedSizeChunkingConfig(FEJOA_FIXED_8K, 8 * 1024)
                    else -> throw Exception("Unknown chunking type")
                }
            }
        }
    }

    class RabinChunkingConfig : ChunkingConfig {
        internal var defaultConfig: RabinChunkingConfig? = null
        override var chunkingType: HashType
            internal set
        internal var targetSize: Int = 0
        internal var minSize: Int = 0
        internal var maxSize: Int = 0

        override fun toByteArray(): ByteArray {
            val buffer = ProtocolBufferLight()
            if (targetSize != defaultConfig!!.targetSize)
                buffer.put(RabinDetailTag.TARGET_CHUNK_SIZE.value, targetSize)
            if (minSize != defaultConfig!!.minSize)
                buffer.put(RabinDetailTag.MIN_CHUNK_SIZE.value, minSize)
            if (maxSize != defaultConfig!!.maxSize)
                buffer.put(RabinDetailTag.MAX_CHUNK_SIZE.value, maxSize)

            val outputStream = ByteArrayOutStream()
            buffer.write(outputStream)
            return outputStream.toByteArray()
        }

        private constructor(type: HashType, targetSize: Int, minSize: Int, maxSize: Int) {
            this.chunkingType = type
            this.targetSize = targetSize
            this.minSize = minSize
            this.maxSize = maxSize
        }

        private constructor(type: HashType, defaultConfig: RabinChunkingConfig?, targetSize: Int,
                            minSize: Int, maxSize: Int) {
            this.chunkingType = type
            this.defaultConfig = defaultConfig
            this.targetSize = targetSize
            this.minSize = minSize
            this.maxSize = maxSize
        }

        override fun getSplitter(factor: Float): ChunkSplitter {
            return RabinSplitter((factor * targetSize).toInt(), (factor * minSize).toInt(), (factor * maxSize).toInt())
        }

        override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
            return DynamicNodeWriteStrategy(getNodeSplitterFactory(), 0, normalizeChunkSize)
        }

        override val isCompact: Boolean
            get() = chunkingType.value and COMPACT_MASK != 0

        override fun clone(): ChunkingConfig {
            return RabinChunkingConfig(chunkingType, defaultConfig, targetSize, minSize, maxSize)
        }

        override fun equals(o: Any?): Boolean {
            if (o !is RabinChunkingConfig)
                return false
            if (o.chunkingType != chunkingType)
                return false
            if (o.minSize != minSize)
                return false
            if (o.targetSize != targetSize)
                return false
            return if (o.maxSize != maxSize) false else true
        }

        override val isDefault: Boolean
            get() = this == defaultConfig

        companion object {

            fun create(type: HashType): RabinChunkingConfig {
                val config = getDefault(type)
                config.defaultConfig = getDefault(type)
                return config
            }

            private fun getDefault(type: HashType): RabinChunkingConfig {
                return when (type) {
                    FEJOA_RABIN_CUSTOM -> getDefault(FEJOA_RABIN_2KB_8KB).also { it.chunkingType = FEJOA_RABIN_CUSTOM }
                    FEJOA_RABIN_2KB_8KB -> RabinChunkingConfig(FEJOA_RABIN_2KB_8KB, 8 * 1024, 2 * 1024,
                            Int.MAX_VALUE / 2)
                    FEJOA_RABIN_2KB_8KB_COMPACT -> RabinChunkingConfig(FEJOA_RABIN_2KB_8KB_COMPACT, 8 * 1024, 2 * 1024,
                            Int.MAX_VALUE / 2)
                    else -> throw Exception("Unknown chunking type")
                }
            }
        }
    }
}