package org.fejoa.storage

import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream


interface RandomDataAccess : AsyncInStream, AsyncOutStream {
    enum class Mode constructor(private var value: Int) {
        READ(0x01),
        WRITE(0x02),
        TRUNCATE(WRITE.value or 0x04),
        APPEND(WRITE.value or 0x08),
        INSERT(WRITE.value or 0x10);

        fun add(otherMode: Mode): Mode {
            this.value = this.value or otherMode.value
            return this
        }

        fun has(otherMode: Mode): Boolean {
            return this.value and otherMode.value == otherMode.value
        }
    }

    val mode: Mode
    fun isOpen(): Boolean
    fun length(): Long
    fun position(): Long
    suspend fun seek(position: Long)

    suspend fun truncate(size: Long)
    suspend fun delete(position: Long, length: Long)
}
