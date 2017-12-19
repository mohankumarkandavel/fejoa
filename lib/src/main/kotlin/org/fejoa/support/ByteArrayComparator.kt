package org.fejoa.support


fun ByteArray.compareTo(other: ByteArray): Int {
    if (size < other.size)
        return -1
    if (size > other.size)
        return 1

    return (size - 1 downTo 0)
            .map { get(it).compareTo(other[it]) }
            .firstOrNull { it != 0 }
            ?: 0
}

class ByteArrayComparator: Comparator<ByteArray> {
    override fun compare(array: ByteArray, other: ByteArray): Int {
        return array.compareTo(other)
    }
}
