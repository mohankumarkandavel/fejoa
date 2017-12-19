package org.fejoa.storage

import org.fejoa.support.OutStream


abstract class ChunkSplitter : OutStream {
    /**
     * The position of the byte that triggered the splitter.
     *
     * If isTriggered is false this variable is meaningless
     */
    var triggerPosition: Long = -1
        private set
    var isTriggered = false
        private set

    protected abstract fun writeInternal(i: Byte): Boolean
    protected abstract fun resetInternal()

    abstract fun newInstance(): ChunkSplitter

    fun reset() {
        triggerPosition = -1
        isTriggered = false
        resetInternal()
    }

    override fun write(byte: Byte): Int {
        if (!isTriggered)
            triggerPosition++

        if (writeInternal(byte))
            isTriggered = true

        return 1
    }

    fun update(i: Byte): Boolean {
        write(i)
        return isTriggered
    }

    override fun flush() {
    }

    override fun close() {
    }
}
