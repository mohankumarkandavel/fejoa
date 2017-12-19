package org.fejoa.support

expect class Random {
    constructor(seed: Long)
    constructor()

    fun read(): Int
    fun read(buffer: ByteArray): Int
    fun readFloat(): Float
}