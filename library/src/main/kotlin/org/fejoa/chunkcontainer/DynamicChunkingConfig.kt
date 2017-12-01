package org.fejoa.chunkcontainer

import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.storage.ChunkSplitter
import org.fejoa.storage.CyclicPolySplitter
import org.fejoa.storage.RabinSplitter
import org.fejoa.support.ByteArrayInStream
import org.fejoa.support.ByteArrayOutStream


abstract class DynamicChunkingConfig : ChunkingConfig {
    internal enum class DynamicChunkingDetailTag(val value: Int) {
        TARGET_CHUNK_SIZE(0),
        MIN_CHUNK_SIZE(1),
        MAX_CHUNK_SIZE(2),
        WINDOW_SIZE(3),

        // Cyclic polynomial config
        SEED(5)
    }

    protected var defaultConfig: DynamicChunkingConfig? = null
    override var chunkingType: HashSpec.HashType
        internal set
    var targetSize: Int = 0
    var minSize: Int = 0
    var maxSize: Int = 0
    var windowSize: Int = 48

    override fun toByteArray(): ByteArray {
        val outputStream = ByteArrayOutStream()
        val buffer = ProtocolBufferLight()
        write(buffer)
        buffer.write(outputStream)
        return outputStream.toByteArray()
    }

    open protected fun write(buffer: ProtocolBufferLight) {
        if (targetSize != defaultConfig!!.targetSize)
            buffer.put(DynamicChunkingDetailTag.TARGET_CHUNK_SIZE.value, targetSize)
        if (minSize != defaultConfig!!.minSize)
            buffer.put(DynamicChunkingDetailTag.MIN_CHUNK_SIZE.value, minSize)
        if (maxSize != defaultConfig!!.maxSize)
            buffer.put(DynamicChunkingDetailTag.MAX_CHUNK_SIZE.value, maxSize)
        if (windowSize != defaultConfig!!.windowSize)
            buffer.put(DynamicChunkingDetailTag.WINDOW_SIZE.value, windowSize)
    }

    open protected fun read(buffer: ProtocolBufferLight) {
        var value = buffer.getLong(DynamicChunkingDetailTag.TARGET_CHUNK_SIZE.value)
        if (value != null)
            targetSize = value.toInt()
        value = buffer.getLong(DynamicChunkingDetailTag.MIN_CHUNK_SIZE.value)
        if (value != null)
            minSize = value.toInt()
        value = buffer.getLong(DynamicChunkingDetailTag.MAX_CHUNK_SIZE.value)
        if (value != null)
            maxSize = value.toInt()
        value = buffer.getLong(DynamicChunkingDetailTag.WINDOW_SIZE.value)
        if (value != null)
            windowSize = value.toInt()
    }

    protected constructor(type: HashSpec.HashType, defaultConfig: DynamicChunkingConfig?, targetSize: Int,
                        minSize: Int, maxSize: Int, windowSize: Int) {
        this.chunkingType = type
        this.defaultConfig = defaultConfig
        this.targetSize = targetSize
        this.minSize = minSize
        this.maxSize = maxSize
        this.windowSize = windowSize
    }

    override fun getNodeWriteStrategy(normalizeChunkSize: Boolean): NodeWriteStrategy {
        return DynamicNodeWriteStrategy(getNodeSplitterFactory(), 0, normalizeChunkSize)
    }

    override val isCompact: Boolean
        get() = chunkingType.value and HashSpec.COMPACT_MASK != 0

    override fun equals(o: Any?): Boolean {
        if (o == null || o !is DynamicChunkingConfig)
            return false
        if (o.chunkingType != chunkingType)
            return false
        if (o.targetSize != targetSize)
            return false
        if (o.minSize != minSize)
            return false
        if (o.maxSize != maxSize)
            return false
        if (o.windowSize != windowSize)
            return false
        return true
    }

    override val isDefault: Boolean
        get() = this == defaultConfig
}


class RabinChunkingConfig : DynamicChunkingConfig {
    companion object {
        fun read(type: HashSpec.HashType, custom: Boolean, extra: ByteArray): RabinChunkingConfig {
            val config = RabinChunkingConfig.create(type)
            if (!custom)
                return config

            val buffer = ProtocolBufferLight()
            buffer.read(ByteArrayInStream(extra))
            config.read(buffer)
            return config
        }

        fun create(type: HashSpec.HashType): RabinChunkingConfig {
            val config = getDefault(type)
            config.defaultConfig = getDefault(type)
            return config
        }

        private fun getDefault(type: HashSpec.HashType): RabinChunkingConfig {
            val windowSize = 48
            return when (type) {
                HashSpec.HashType.FEJOA_RABIN_CUSTOM -> {
                    getDefault(HashSpec.HashType.FEJOA_RABIN_2KB_8KB).also {
                        it.chunkingType = HashSpec.HashType.FEJOA_RABIN_CUSTOM
                    }
                }
                HashSpec.HashType.FEJOA_RABIN_2KB_8KB,
                HashSpec.HashType.FEJOA_RABIN_2KB_8KB_COMPACT -> RabinChunkingConfig(type, 8 * 1024,
                        2 * 1024, Int.MAX_VALUE / 2, windowSize)

                else -> throw Exception("Unknown chunking type")
            }
        }
    }

    private constructor(type: HashSpec.HashType, targetSize: Int, minSize: Int, maxSize: Int, windowSize: Int)
            : this(type, null, targetSize, minSize, maxSize, windowSize)

    private constructor(type: HashSpec.HashType, defaultConfig: DynamicChunkingConfig?, targetSize: Int,
                        minSize: Int, maxSize: Int, windowSize: Int)
             : super(type, defaultConfig, targetSize, minSize, maxSize, windowSize)

    override suspend fun getSplitter(factor: Float): ChunkSplitter {
        return RabinSplitter((factor * targetSize).toInt(), (factor * minSize).toInt(), (factor * maxSize).toInt(),
                windowSize)
    }

    override fun clone(): ChunkingConfig {
        return RabinChunkingConfig(chunkingType, defaultConfig, targetSize, minSize, maxSize, windowSize)
    }
}

class CyclicPolyChunkingConfig : DynamicChunkingConfig {
    companion object {
        fun read(type: HashSpec.HashType, custom: Boolean, extra: ByteArray): CyclicPolyChunkingConfig {
            val config = CyclicPolyChunkingConfig.create(type)
            if (!custom)
                return config

            val buffer = ProtocolBufferLight()
            buffer.read(ByteArrayInStream(extra))
            config.read(buffer)
            return config
        }

        fun create(type: HashSpec.HashType): CyclicPolyChunkingConfig {
            val config = getDefault(type)
            config.defaultConfig = getDefault(type)
            return config
        }

        private fun getDefault(type: HashSpec.HashType): CyclicPolyChunkingConfig {
            val windowSize = 48
            return when (type) {
                HashSpec.HashType.FEJOA_CYCLIC_POLY_2KB_8KB -> {
                    CyclicPolyChunkingConfig(type, 8 * 1024, 2 * 1024,
                            Int.MAX_VALUE / 2, windowSize)
                }
                else -> throw Exception("Unknown chunking type")
            }
        }
    }

    var seed = ByteArray(0)

    private constructor(type: HashSpec.HashType, targetSize: Int, minSize: Int, maxSize: Int, windowSize: Int)
            : this(type, null, targetSize, minSize, maxSize, windowSize)

    private constructor(type: HashSpec.HashType, defaultConfig: DynamicChunkingConfig?, targetSize: Int,
                        minSize: Int, maxSize: Int, windowSize: Int)
            : super(type, defaultConfig, targetSize, minSize, maxSize, windowSize)

    override fun write(buffer: ProtocolBufferLight) {
        super.write(buffer)

        buffer.put(DynamicChunkingDetailTag.SEED.value, seed)
    }

    override fun read(buffer: ProtocolBufferLight) {
        super.read(buffer)

        buffer.getBytes(DynamicChunkingDetailTag.SEED.value)?.also { seed = it}
    }

    override suspend fun getSplitter(factor: Float): ChunkSplitter {
        return CyclicPolySplitter.create(seed, (factor * targetSize).toInt(), (factor * minSize).toInt(),
                (factor * maxSize).toInt(), windowSize)
    }

    override fun clone(): ChunkingConfig {
        val clone = CyclicPolyChunkingConfig(chunkingType, defaultConfig, targetSize, minSize, maxSize, windowSize)
        clone.seed = seed.copyOf()
        return clone
    }
}