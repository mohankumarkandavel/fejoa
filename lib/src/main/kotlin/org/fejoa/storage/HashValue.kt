package org.fejoa.storage

import org.fejoa.support.assert
import org.fejoa.support.toHex


class HashValue : Comparable<HashValue> {
    val bytes: ByteArray

    constructor(hash: ByteArray) {
        this.bytes = hash
    }

    constructor(hashSize: Int) {
        this.bytes = ByteArray(hashSize)
    }

    constructor(hash: HashValue) {
        this.bytes = hash.bytes.copyOf(hash.size())
    }

    fun clone(): HashValue = HashValue(bytes.copyOf())

    override fun hashCode(): Int {
        if (bytes.size > 0)
            return bytes[0].toInt()
        return 0
    }

    override fun equals(o: Any?): Boolean {
        return if (o !is HashValue) false else bytes.contentEquals(o.bytes)
    }

    val isZero: Boolean
        get() {
            for (i in bytes.indices) {
                if (bytes[i].toInt() != 0)
                    return false
            }
            return true
        }

    fun size(): Int {
        return bytes.size
    }

    fun toHex(): String {
        return bytes.toHex()
    }

    override fun toString(): String {
        return toHex()
    }

    override fun compareTo(value: HashValue): Int {
        val theirHash = value.bytes
        assert(theirHash.size == bytes.size)

        for (i in bytes.indices) {
            val ours = bytes[i].toInt() and 0xFF
            val theirs = theirHash[i].toInt() and 0xFF
            if (ours != theirs)
                return ours - theirs
        }
        return 0
    }

    companion object {
        fun fromHex(hash: String): HashValue {
            return HashValue(org.fejoa.support.fromHex(hash))
        }
    }
}
