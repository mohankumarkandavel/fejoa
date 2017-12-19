package org.fejoa.support


interface AsyncCloseable {
    suspend fun close() {}
}

suspend fun <T, C : AsyncCloseable>autoclose(closeable: C, block: suspend (closeable: C) -> T): T {
    val result = block.invoke(closeable)
    closeable.close()
    return result
}
