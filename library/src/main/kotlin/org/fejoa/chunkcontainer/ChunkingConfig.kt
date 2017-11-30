package org.fejoa.chunkcontainer

import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.storage.ChunkSplitter
import org.fejoa.storage.FixedBlockSplitter
import org.fejoa.support.ByteArrayInStream
import org.fejoa.support.ByteArrayOutStream


interface ChunkingConfig {
    fun getNodeSplitterFactory(): NodeSplitterFactory {
        return HashSpec.DefaultNodeSplitter(this, isCompact)
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
    val chunkingType: HashSpec.HashType
    val isCompact: Boolean
    val isDefault: Boolean
}

class NonChunkingConfig (chunkingType: HashSpec.HashType): ChunkingConfig {
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

    override val chunkingType: HashSpec.HashType = chunkingType
    override val isDefault: Boolean = false
    override val isCompact: Boolean = false
}


class FixedSizeChunkingConfig : ChunkingConfig {
    internal enum class FixedSizeDetailTag(val value: Int) {
        SIZE(0)
    }

    override var chunkingType = HashSpec.HashType.FEJOA_FIXED_CUSTOM
        private set
    internal var size: Int = 0
    internal var defaultConfig: FixedSizeChunkingConfig? = null

    private constructor(type: HashSpec.HashType, size: Int) {
        this.chunkingType = type
        this.size = size
    }

    override fun getSplitter(factor: Float): ChunkSplitter {
        return FixedBlockSplitter((factor * size).toInt())
    }

    override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
        return FixedSizeNodeWriteStrategy(size, getNodeSplitterFactory(), 0, normalizeChunkSize)
    }

    override val isCompact: Boolean
        get() = chunkingType.value and HashSpec.COMPACT_MASK != 0

    override fun toByteArray(): ByteArray {
        val buffer = ProtocolBufferLight()
        if (size != defaultConfig!!.size)
            buffer.put(FixedSizeDetailTag.SIZE.value, size)
        val outputStream = ByteArrayOutStream()
        buffer.write(outputStream)
        return outputStream.toByteArray()
    }

    private constructor(type: HashSpec.HashType, defaultConfig: FixedSizeChunkingConfig?, size: Int) {
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
        fun read(type: HashSpec.HashType, custom: Boolean, extra: ByteArray): FixedSizeChunkingConfig {
            val config = FixedSizeChunkingConfig.create(type)
            if (!custom)
                config

            val inputStream = ByteArrayInStream(extra)
            val buffer = ProtocolBufferLight()
            buffer.read(inputStream)
            val value = buffer.getLong(FixedSizeDetailTag.SIZE.value)
            if (value != null)
                config.size = value.toInt()
            return config
        }

        fun create(type: HashSpec.HashType): FixedSizeChunkingConfig {
            val config = getDefault(type)
            config.defaultConfig = getDefault(type)
            return config
        }

        private fun getDefault(type: HashSpec.HashType): FixedSizeChunkingConfig {
            return when (type) {
                HashSpec.HashType.FEJOA_FIXED_CUSTOM -> getDefault(HashSpec.HashType.FEJOA_FIXED_8K).also { it.chunkingType = HashSpec.HashType.FEJOA_FIXED_CUSTOM }
                HashSpec.HashType.FEJOA_FIXED_8K -> FixedSizeChunkingConfig(HashSpec.HashType.FEJOA_FIXED_8K, 8 * 1024)
                else -> throw Exception("Unknown chunking type")
            }
        }
    }
}