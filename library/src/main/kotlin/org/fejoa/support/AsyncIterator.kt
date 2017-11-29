package org.fejoa.support


interface AsyncIterator<out T> {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}

suspend fun <T> AsyncIterator<T>.getAll(): List<T> {
    val list = ArrayList<T>()
    while (this.hasNext())
        list.add(this.next())
    return list
}