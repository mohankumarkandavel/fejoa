package org.fejoa.support

import kotlin.js.Math.random


class RandomJS {

    constructor() {
    }

    constructor(seed: Long) {
        TODO()
    }

    fun read(): Int {
        return readDouble().toInt()
    }

    fun read(buffer: ByteArray): Int {
        for (i in 0 until buffer.size)
            buffer[0] = read().toByte()
        return buffer.size
    }

    fun readFloat(): Float {
        return readDouble().toFloat()
    }

    fun readDouble(): Double {
        return random()
    }
}

actual typealias Random = RandomJS