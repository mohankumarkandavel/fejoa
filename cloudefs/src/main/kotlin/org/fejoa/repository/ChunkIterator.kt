package org.fejoa.repository

import org.fejoa.chunkcontainer.ChunkContainerNode
import org.fejoa.support.AsyncIterator
import org.fejoa.storage.HashValue

class CombinedIterator<T> : AsyncIterator<T> {
    private val queue: MutableList<AsyncIterator<T>> = ArrayList()

    fun add(iterator: AsyncIterator<T>) {
        queue.add(iterator)
    }

    suspend override fun hasNext(): Boolean {
        while (!queue.isEmpty()) {
            if (queue[0].hasNext())
                return true
            else
                queue.removeAt(0)
        }
        return false
    }

    suspend override fun next(): T {
        return queue[0].next()
    }
}

fun <T>Iterator<T>.asAsyncIterator(): AsyncIterator<T> {
    val that = this
    return object : AsyncIterator<T> {
        suspend override fun hasNext(): Boolean = that.hasNext()
        suspend override fun next(): T = that.next()
    }
}

suspend fun ChunkContainerNode.getChunkIterator(): AsyncIterator<HashValue> {
    val combinedIterator = CombinedIterator<HashValue>()
    val iterator = chunkPointers.asSequence().map {
        it.boxHash
    }.iterator().asAsyncIterator()
    combinedIterator.add(iterator)

    // dynamically load children
    val that = this
    if (!isDataLeafNode) {
        chunkPointers.forEach {
            combinedIterator.add(object : AsyncIterator<HashValue> {
                val iterator: AsyncIterator<HashValue>? = null

                suspend fun ensureIterator(): AsyncIterator<HashValue> {
                    return this.iterator?.let { return it }
                            ?: ChunkContainerNode.read(blobAccessor, that, it).getChunkIterator()
                }

                suspend override fun hasNext(): Boolean = ensureIterator().hasNext()
                suspend override fun next(): HashValue = ensureIterator().next()

            })
        }
    }

    return combinedIterator
}
