package org.fejoa.support

class RandomJVM {
    private val random: java.util.Random

    constructor() {
        random = java.util.Random()
    }

    constructor(seed: Long) {
        random = java.util.Random(seed)
    }

    fun read(): Int {
        return random.nextInt()
    }

    fun read(buffer: ByteArray): Int {
        random.nextBytes(buffer)
        return buffer.size
    }

    fun readFloat(): Float {
        return random.nextFloat()
    }
}

actual typealias Random = RandomJVM