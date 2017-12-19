package org.fejoa.storage


abstract class DynamicSplitter(val targetChunkSize: Int = CHUNK_8KB,
                               val minChunkSize: Int = 128,
                               val maxChunkSize: Int = Int.MAX_VALUE,
                               val windowSize: Int = 48) : ChunkSplitter() {
    private var chunkSize: Long = 0

    companion object {
        val CHUNK_1KB = 1024
        val CHUNK_8KB = 8 * CHUNK_1KB
        val CHUNK_16KB = 16 * CHUNK_1KB
        val CHUNK_32KB = 32 * CHUNK_1KB
        val CHUNK_64KB = 64 * CHUNK_1KB
        val CHUNK_128KB = 128 * CHUNK_1KB

        private val MASK = 0xFFFFFFFFL
    }

    abstract fun updateFingerprint(byte: Byte): Long
    abstract fun resetFingerprint()

    override protected fun writeInternal(i: Byte): Boolean {
        chunkSize++
        if (chunkSize < minChunkSize - windowSize)
            return false
        val fingerprint = updateFingerprint(i)
        if (chunkSize < minChunkSize)
            return false
        if (chunkSize >= maxChunkSize)
            return true
        return (fingerprint and MASK) < (MASK / targetChunkSize)
    }

    override protected fun resetInternal() {
        chunkSize = 0
        resetFingerprint()
    }
}
