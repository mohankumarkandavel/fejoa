package org.rabinfingerprint.datastructures

/**
 * A fast but unsafe circular byte queue.
 *
 * There is no enforcement that the indices are valid, and it is easily possible
 * to overflow when adding or polling. But, this is faster than Queue<Byte> by a
 * factor of 5 or so.
</Byte> */
class CircularByteQueue(private val capacity: Int) {
    private var size = 0
    private var head = 0
    private var tail = 0
    private val bytes: ByteArray

    init {
        this.bytes = ByteArray(capacity)
    }

    /**
     * Adds the byte to the queue
     */
    fun add(b: Byte) {
        bytes[head] = b
        head++
        head %= capacity
        size++
    }

    /**
     * Removes and returns the next byte in the queue
     */
    fun poll(): Byte {
        val b = bytes[tail]
        tail++
        tail %= capacity
        size--
        return b
    }

    /**
     * Resets the queue to its original state but DOES NOT clear the array of
     * bytes.
     */
    fun clear() {
        head = 0
        tail = 0
        size = 0
    }

    /**
     * Returns the number of elements that have been added to this queue minus
     * those that have been removed.
     */
    fun size(): Int {
        return size
    }

    /**
     * Returns the capacity of this queue -- i.e. the total number of bytes that
     * can be stored without overflowing.
     */
    fun capacity(): Int {
        return capacity
    }

    val isFull: Boolean
        get() = size == capacity
}
