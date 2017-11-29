package org.fejoa.storage


class FixedBlockSplitter(val blockSize: Int) : ChunkSplitter() {
    private var nBytesInBlock: Int = 0

    override fun writeInternal(i: Byte): Boolean {
        nBytesInBlock++
        return if (nBytesInBlock >= blockSize) true else false

    }

    override fun resetInternal() {
        nBytesInBlock = 0
    }

    override fun newInstance(): ChunkSplitter {
        return FixedBlockSplitter(blockSize)
    }
}
