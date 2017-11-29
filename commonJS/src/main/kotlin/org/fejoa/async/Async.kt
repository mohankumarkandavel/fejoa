package org.fejoa.async

import org.fejoa.support.Future
import kotlin.coroutines.experimental.*
import kotlin.js.Promise


suspend fun <T> Promise<T>.await() = suspendCoroutine<T> { cont ->
    then({ value -> cont.resume(value) },
            { exception -> cont.resumeWithException(exception) })
}

fun <T> async(block: suspend () -> T): Promise<T> = Promise { resolve, reject ->
    block.startCoroutine(object : Continuation<T> {
        override val context: CoroutineContext get() = EmptyCoroutineContext
        override fun resume(value: T) { resolve(value) }
        override fun resumeWithException(exception: Throwable) { reject(exception) }
    })
}

fun launch(block: suspend () -> Unit) {
    async(block).catch { exception -> console.log("Failed with $exception") }
}

fun <T>toPromise(block: suspend () -> T): Promise<T> {
    return Promise({resolve, reject ->
        launch {
            try {
                val result = block()
                resolve(result)
            } catch (e: Exception) {
                reject(e)
            }
        }
    })
}

fun <T>Promise<T>.toFuture(): Future<T> {
    val promise = Future<T>()
    this.then({
        promise.setResult(it)
    }, {
        promise.setError(it)
    })
    return promise
}
