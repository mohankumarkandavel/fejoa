package org.fejoa.chunkcontainer

import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.repository.readVarIntDelimited
import org.fejoa.repository.writeVarIntDelimited
import org.fejoa.chunkcontainer.HashSpec.HashType.*
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.HashOutStreamFactory
import org.fejoa.crypto.SHA256Factory
import org.fejoa.storage.*
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
    constructor() : this(DEFAULT)
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
        FEJOA_RABIN_2KB_8KB_COMPACT(FEJOA_RABIN_2KB_8KB.value or COMPACT_MASK),
        FEJOA_CYCLIC_POLY_2KB_8KB(CC_HASH_OFFSET + 32),
    }

    var chunkingConfig: ChunkingConfig = chunkingConfig
        private set
    val compact: Boolean
        get() = chunkingConfig.isCompact
    val type: HashType
        get() = chunkingConfig.chunkingType

    companion object {
        val DEFAULT = FEJOA_CYCLIC_POLY_2KB_8KB

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

        private fun getDefaultChunkingConfig(type: HashType): ChunkingConfig {
            return when (type) {
                SHA_256 -> NonChunkingConfig(SHA_256)
                FEJOA_FIXED_8K -> FixedSizeChunkingConfig.create(type)
                FEJOA_FIXED_CUSTOM -> throw Exception("Not allowed")
                FEJOA_RABIN_CUSTOM -> throw Exception("Not allowed")
                FEJOA_RABIN_2KB_8KB,
                FEJOA_RABIN_2KB_8KB_COMPACT -> RabinChunkingConfig.create(type)
                FEJOA_CYCLIC_POLY_2KB_8KB -> CyclicPolyChunkingConfig.create(type)
            }
        }

        suspend private fun getChunkingConfig(type: HashType, extra: ByteArray): ChunkingConfig {
            val custom = type.value and CUSTOM_HASH_MASK != 0
            return when (type) {
                SHA_256 -> NonChunkingConfig(SHA_256)
                FEJOA_FIXED_CUSTOM,
                FEJOA_FIXED_8K -> FixedSizeChunkingConfig.read(type, custom, extra)
                FEJOA_RABIN_CUSTOM,
                FEJOA_RABIN_2KB_8KB,
                FEJOA_RABIN_2KB_8KB_COMPACT -> RabinChunkingConfig.read(type, custom, extra)
                FEJOA_CYCLIC_POLY_2KB_8KB -> CyclicPolyChunkingConfig.read(type, custom, extra)
            }
        }
    }

    suspend fun write(outStream: AsyncOutStream): Int {
        var rawByte = type.value and HASH_TYPE_MASK
        var bytesWritten = outStream.writeByte(rawByte.toByte())
        if (type.value and CUSTOM_HASH_MASK != 0)
            bytesWritten += outStream.writeVarIntDelimited(chunkingConfig.toByteArray())
        return bytesWritten
    }

    suspend fun getHashOutStream(): AsyncHashOutStream {
        return when(this.type) {
            SHA_256 -> SHA256Factory().create()
            FEJOA_FIXED_CUSTOM,
            FEJOA_FIXED_8K,
            FEJOA_RABIN_CUSTOM,
            FEJOA_RABIN_2KB_8KB,
            FEJOA_RABIN_2KB_8KB_COMPACT,
            FEJOA_CYCLIC_POLY_2KB_8KB -> ChunkHash.create(this)
        }
    }

    fun getBaseHashFactory(): HashOutStreamFactory = when (type) {
        SHA_256,
        FEJOA_FIXED_CUSTOM,
        FEJOA_FIXED_8K,
        FEJOA_RABIN_CUSTOM,
        FEJOA_RABIN_2KB_8KB,
        FEJOA_RABIN_2KB_8KB_COMPACT,
        FEJOA_CYCLIC_POLY_2KB_8KB -> object : HashOutStreamFactory {
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

        override suspend fun create(level: Int): ChunkSplitter {
            return config.getSplitter(nodeSizeFactor(level))
        }
    }

    fun getNodeSplitterFactory(): NodeSplitterFactory {
        return chunkingConfig.getNodeSplitterFactory()
    }

    fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
        return chunkingConfig.getNodeWriteStrategy(normalizeChunkSize)
    }

    fun setRabinChunking(type: HashType): RabinChunkingConfig {
        chunkingConfig = RabinChunkingConfig.create(type)
        return chunkingConfig as RabinChunkingConfig
    }

    fun setRabinChunking(type: HashType, targetSize: Int, minSize: Int, maxSize: Int): RabinChunkingConfig {
        val config = RabinChunkingConfig.create(type)
        chunkingConfig = config
        config.targetSize = targetSize
        config.minSize = minSize
        config.maxSize = maxSize
        return config
    }

    fun setRabinChunking(targetSize: Int, minSize: Int): RabinChunkingConfig {
        val config = RabinChunkingConfig.create(FEJOA_RABIN_CUSTOM)
        chunkingConfig = config
        config.targetSize = targetSize
        config.minSize = minSize
        return config
    }

    fun setFixedSizeChunking(size: Int): FixedSizeChunkingConfig {
        val config = FixedSizeChunkingConfig.create(FEJOA_FIXED_CUSTOM)
        chunkingConfig = config
        config.size = size
        return config
    }
}