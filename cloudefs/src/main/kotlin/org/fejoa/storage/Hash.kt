package org.fejoa.storage

import org.fejoa.chunkcontainer.*
import org.fejoa.crypto.AsyncHashOutStream
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.HashOutStreamFactory
import org.fejoa.crypto.SHA256Factory
import org.fejoa.repository.readVarIntDelimited
import org.fejoa.repository.writeVarIntDelimited
import org.fejoa.support.*


/**
 * |HashSpec|
 * |hash (32|
 */
class Hash(val spec: HashSpec, var value: HashValue) {
    fun clone(): Hash = Hash(spec.clone(), value.clone())

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Hash)
            return false
        return value == other.value
    }

    companion object {
        fun createChild(parent: HashSpec): Hash {
            return Hash(parent.createChild(), Config.newDataHash())
        }

        suspend fun read(inStream: AsyncInStream, parent: HashSpec?): Hash {
            val info = HashSpec.read(inStream, parent)
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
class HashSpec private constructor(chunkingConfig: ChunkingConfig) {
    /**
     * A HashSpec can have a parent config. This parent is used for repository wide global parameters. For example,
     * the chunking seed value is only stored once and then passed on to child HashSpecs. However, it is still possible
     * to use individual seed values for single objects.
     *
     * @param type the HashSpec is initialized with the default settings for the hash type
     * @param parent the ChunkingConfig this config is derived from
     */
    constructor(type: HashType, parent: ChunkingConfig?) : this(getDefaultChunkingConfig(type)) {
        if (parent != null)
            chunkingConfig.setParent(parent)
    }


    fun clone(): HashSpec {
        return HashSpec(chunkingConfig.clone())
    }

    fun createChild(): HashSpec {
        return HashSpec(chunkingConfig.createChild())
    }

    enum class HashType(val value: Int) {
        SHA_256(0),
        // chunking hashes:
        FEJOA_FIXED_8K(CC_HASH_OFFSET + 1),
        FEJOA_RABIN_2KB_8KB(CC_HASH_OFFSET + 10),
        FEJOA_CYCLIC_POLY_2KB_8KB(CC_HASH_OFFSET + 30)
    }

    var chunkingConfig: ChunkingConfig = chunkingConfig
        private set
    val compact: Boolean
        get() = chunkingConfig.isCompact
    val type: HashType
        get() = chunkingConfig.chunkingType

    companion object {
        val DEFAULT = HashType.FEJOA_CYCLIC_POLY_2KB_8KB

        val EXTENSION_HASH_MASK = 1 shl 7
        val COMPACT_HASH_MASK = 1 shl 6
        val HASH_TYPE_MASK = 0x3F
        val CC_HASH_OFFSET = 10

        fun createCyclicPoly(type: HashType, seed: ByteArray): HashSpec {
            return HashSpec(CyclicPolyChunkingConfig.create(type, seed, null))
        }

        suspend fun read(inStream: AsyncInStream, parent: HashSpec?): HashSpec {
            val rawByte = inStream.read()

            val typeValue = rawByte and HASH_TYPE_MASK
            val type = HashType.values().firstOrNull { it.value == typeValue }
                    ?: throw IOException("Unknown HashType $typeValue")

            val extra = if (rawByte and EXTENSION_HASH_MASK != 0) inStream.readVarIntDelimited().first
                                    else ByteArray(0)
            val compact = rawByte and COMPACT_HASH_MASK != 0

            val config = readChunkingConfig(type, extra, parent?.chunkingConfig)
            if (compact)
                config.isCompact = compact
            return HashSpec(config)
        }

        private fun getDefaultChunkingConfig(type: HashType): ChunkingConfig {
            return  when (type) {
                HashType.SHA_256 -> NonChunkingConfig(HashType.SHA_256)
                HashType.FEJOA_FIXED_8K -> FixedSizeChunkingConfig.create(type)
                HashType.FEJOA_RABIN_2KB_8KB -> RabinChunkingConfig.create(type)
                HashType.FEJOA_CYCLIC_POLY_2KB_8KB -> CyclicPolyChunkingConfig.create(type, ByteArray(0), null)
            }
        }

        suspend private fun readChunkingConfig(type: HashType, extra: ByteArray, parent: ChunkingConfig?): ChunkingConfig {
            val hasExt = type.value and EXTENSION_HASH_MASK != 0
            return when (type) {
                HashType.SHA_256 -> NonChunkingConfig(HashType.SHA_256)
                HashType.FEJOA_FIXED_8K -> FixedSizeChunkingConfig.read(type, hasExt, extra)
                HashType.FEJOA_RABIN_2KB_8KB -> RabinChunkingConfig.read(type, hasExt, extra)
                HashType.FEJOA_CYCLIC_POLY_2KB_8KB -> {
                    CyclicPolyChunkingConfig.read(type, hasExt, extra, parent as? CyclicPolyChunkingConfig)
                }
            }
        }
    }

    suspend fun write(outStream: AsyncOutStream): Int {
        var rawByte = chunkingConfig.chunkingType.value and HASH_TYPE_MASK
        if (chunkingConfig.hasExtension())
            rawByte = rawByte or EXTENSION_HASH_MASK
        if (chunkingConfig.isCompact)
            rawByte = rawByte or COMPACT_HASH_MASK
        var bytesWritten = outStream.writeByte(rawByte.toByte())
        if (chunkingConfig.hasExtension())
            bytesWritten += outStream.writeVarIntDelimited(chunkingConfig.getExtensionData())
        return bytesWritten
    }

    suspend fun getHashOutStream(): AsyncHashOutStream {
        return when(this.type) {
            HashType.SHA_256 -> SHA256Factory().create()
            HashType.FEJOA_FIXED_8K,
            HashType.FEJOA_RABIN_2KB_8KB,
            HashType.FEJOA_CYCLIC_POLY_2KB_8KB -> ChunkHash.create(this)
        }
    }

    fun getBaseHashFactory(): HashOutStreamFactory = when (type) {
        HashType.SHA_256,
        HashType.FEJOA_FIXED_8K,
        HashType.FEJOA_RABIN_2KB_8KB,
        HashType.FEJOA_CYCLIC_POLY_2KB_8KB -> object : HashOutStreamFactory {
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
        val config = RabinChunkingConfig.create(HashType.FEJOA_RABIN_2KB_8KB)
        chunkingConfig = config
        config.targetSize = targetSize
        config.minSize = minSize
        return config
    }

    fun setFixedSizeChunking(size: Int): FixedSizeChunkingConfig {
        val config = FixedSizeChunkingConfig.create(HashType.FEJOA_FIXED_8K)
        chunkingConfig = config
        config.size = size
        return config
    }
}