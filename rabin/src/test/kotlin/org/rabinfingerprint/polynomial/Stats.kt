package org.rabinfingerprint.polynomial

class Stats {
    private var count = 0L
    private var sum = 0.0

    //@Synchronized
    fun add(value: Double) {
        sum += value
        count++
    }

    //@Synchronized
    fun average(): Double {
        return if (count == 0L) {
            0.0
        } else sum / count
    }

    //@Synchronized
    fun sum(): Double {
        return sum
    }

    //@Synchronized
    fun count(): Long {
        return count
    }
}