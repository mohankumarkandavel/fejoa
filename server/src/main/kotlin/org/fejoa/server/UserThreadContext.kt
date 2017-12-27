package org.fejoa.server

import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newSingleThreadContext


/**
 * Provides a per user context thread to synchronize calls to a single user account
 */
object UserThreadContext {
    private class RefCountValue<out T>(val value: T, var count: Int = 0)
    private val map: MutableMap<String, RefCountValue<CoroutineDispatcher>> = HashMap()


    private fun acquireDispatcher(user: String): CoroutineDispatcher = synchronized(map) {
        val ref = map[user]
                ?: RefCountValue(newSingleThreadContext(user)).also { map[user] = it }
        ref.count ++
        ref.value
    }

    private fun releaseDispatcher(user: String) {
        val ref = map[user] ?: throw Exception("Internal error")
        ref.count--
        if (ref.count == 0)
            map.remove(user)
    }

    suspend fun <T>run(user: String, block: suspend () -> T): T {
        val dispatcher = acquireDispatcher(user)
        return try {
            async(dispatcher) {
                return@async block.invoke()
            }.await()
        } finally {
            releaseDispatcher(user)
        }
    }
}