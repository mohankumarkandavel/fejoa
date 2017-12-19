package org.fejoa.chunkcontainer

import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.storage.ChunkSplitter
import org.fejoa.storage.FixedBlockSplitter
import org.fejoa.support.ByteArrayOutStream
import org.fejoa.storage.HashSpec


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
    suspend fun getSplitter(factor: Float): ChunkSplitter
    fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy
    fun getExtensionData(): ByteArray
    fun clone(): ChunkingConfig
    /**
     * Derives a child config from this (parent) chunking config
     */
    fun createChild(): ChunkingConfig
    val chunkingType: HashSpec.HashType
    var isCompact: Boolean
    fun hasExtension(): Boolean {
        return !isDefault
    }
    val isDefault: Boolean

    // set the parent this config is based on, e.g. the parent that provides the chunking seed
    fun setParent(parent: ChunkingConfig) {

    }
}

class NonChunkingConfig (chunkingType: HashSpec.HashType): ChunkingConfig {
    override suspend fun getSplitter(factor: Float): ChunkSplitter {
        throw Exception("Not a chunking hash")
    }

    override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
        throw Exception("Not a chunking hash")
    }

    override fun getExtensionData(): ByteArray {
        return ByteArray(0)
    }

    override fun createChild(): ChunkingConfig {
        val clone = clone()
        clone.setParent(this)
        return clone
    }

    override fun clone(): ChunkingConfig {
        return NonChunkingConfig(chunkingType)
    }

    override val chunkingType: HashSpec.HashType = chunkingType
    override val isDefault: Boolean = true
    override var isCompact: Boolean = false
        set(value) = throw Exception("Not allowed")
}


class FixedSizeChunkingConfig : ChunkingConfig {
    internal enum class FixedSizeDetailTag(val value: Int) {
        SIZE(0)
    }

    override var chunkingType = HashSpec.HashType.FEJOA_FIXED_8K
        private set
    internal var size: Int = 0
    internal var defaultConfig: FixedSizeChunkingConfig? = null

    private constructor(type: HashSpec.HashType, defaultConfig: FixedSizeChunkingConfig?, size: Int) {
        this.chunkingType = type
        this.defaultConfig = defaultConfig
        this.size = size
    }

    override suspend fun getSplitter(factor: Float): ChunkSplitter {
        return FixedBlockSplitter((factor * size).toInt())
    }

    override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
        return FixedSizeNodeWriteStrategy(size, getNodeSplitterFactory(), 0, normalizeChunkSize)
    }

    override var isCompact: Boolean = false

    override fun getExtensionData(): ByteArray {
        val buffer = ProtocolBufferLight()
        if (size != defaultConfig!!.size)
            buffer.put(FixedSizeDetailTag.SIZE.value, size)
        val outputStream = ByteArrayOutStream()
        buffer.write(outputStream)
        return outputStream.toByteArray()
    }

    override fun createChild(): ChunkingConfig {
        val clone = clone()
        clone.setParent(this)
        return clone
    }

    override fun clone(): ChunkingConfig {
        return FixedSizeChunkingConfig(chunkingType, defaultConfig, size)
    }

    override val isDefault: Boolean
        get() = this == defaultConfig

    companion object {
        fun read(type: HashSpec.HashType, custom: Boolean, extra: ByteArray): FixedSizeChunkingConfig {
            val config = FixedSizeChunkingConfig.create(type)
            if (!custom)
                config

            val buffer = ProtocolBufferLight(extra)
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
                HashSpec.HashType.FEJOA_FIXED_8K
                    -> FixedSizeChunkingConfig(HashSpec.HashType.FEJOA_FIXED_8K, null,8 * 1024)
                else -> throw Exception("Unknown chunking type")
            }
        }
    }
}