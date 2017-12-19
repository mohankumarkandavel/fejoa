package org.fejoa.support

import kotlin.coroutines.experimental.suspendCoroutine

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.startCoroutine


interface Executor {
    fun run(task: () -> Unit)
}

class NowExecutor : Executor {
    override fun run(task: () -> Unit) {
        task()
    }
}

class Future<T>(executor: Executor = NowExecutor(), private val task: (() -> T)? = null,
                private val cancelCallback: (() -> Unit)? = null) {
    companion object {
        fun <T>completedFuture(value: T): Future<T> {
            val future = Future<T>()
            future.setResult(value)
            return future
        }

        fun <T>failedFuture(message: String): Future<T> {
            val future = Future<T>()
            future.setError(Exception(message))
            return future
        }
    }

    private interface Listener<in T> {
        fun onResult(result: T)
        fun onError(error: Throwable)
    }

    interface Result<T>

    class Running<T>: Result<T>
    class Ok<T>(val result: T): Result<T>
    class Error<T>(val error: Throwable): Result<T>

    class CancelationException(message: String) : Exception(message)

    var result: Result<T> = Running()
        private set

    private var parent: Future<*>? = null
    private val listeners = ArrayList<Listener<T>>()

    init {
        if (task != null) {
            executor.run {
                try {
                    setResult(task.invoke())
                } catch (e: Throwable) {
                    setError(e)
                }
            }
        }
    }

    fun finished(): Boolean {
        return result !is Running
    }

    fun setResult(result: T): Boolean {
        if (finished()) return false
        this.result = Ok(result)
        whenCompleted()
        return true
    }

    fun setError(error: Throwable): Boolean {
        if (finished()) return false
        result = Error(error)
        whenCompleted()
        return true
    }

    // TODO cancel children even if we already finished?
    fun cancel() {
        if (finished()) return
        setError(CancelationException("Canceled"))
        cancelCallback?.invoke()
        parent?.cancel()
        whenCompleted()
    }

    private fun whenCompleted() {
        listeners.forEach { propagateResult(it) }
        listeners.clear()
    }

    private fun propagateResult(listener: Listener<T>) {
        when (result) {
            is Error -> listener.onError((result as Error).error)
            is Ok -> listener.onResult((result as Ok).result)
        }
    }

    private fun complete(result: Result<T>) {
        this.result = result
        whenCompleted()
    }

    fun <R>bindAsync(executor: Executor, method: (T) -> Future<R>): Future<R> {
        val resultPromise = Future<R>()
        resultPromise.parent = this
        val listener = object: Listener<T> {
            override fun onResult(result: T) {
                executor.run {
                    resultPromise.complete(method.invoke(result).result)
                }
            }

            override fun onError(error: Throwable) {
                if (error is CancelationException)
                    resultPromise.cancel()
                resultPromise.setError(error)
            }
        }
        if (finished()) {
            propagateResult(listener)
        } else {
            listeners.add(listener)
        }
        return resultPromise
    }

    fun <R>thenAsync(executor: Executor, method: (T) -> R): Future<R> {
        return bindAsync(executor) {
            val promise = Future<R>(cancelCallback = cancelCallback)
            when (result) {
                is Error -> promise.setError((result as Error).error)
                is Ok -> {
                    try {
                        val value = method.invoke((result as Ok).result)
                        promise.setResult(value)
                    } catch (e: Throwable) {
                        promise.setError(e)
                    }
                }
            }
            promise
        }
    }

    fun <R>bind(method: (T) -> Future<R>): Future<R> {
        return bindAsync(NowExecutor(), method)
    }

    fun <R>then(method: (T) -> R): Future<R> {
        return thenAsync(NowExecutor(), method)
    }

    /*
    fun <R>thenSuspend(method: suspend (T) -> R): Future<R> {
        val promise = Future<R>(cancelCallback = cancelCallback)
        whenCompletedSimple { result, error ->
            if (error != null) {
                promise.setError(error)
                return@whenCompletedSimple
            }
            if (result != null) {
                launch {
                    try {
                        val methodResult = method(result)
                        promise.setResult(methodResult)
                    } catch (e: Exception) {
                        promise.setError(e)
                    }
                }
            }
        }
        return promise
    }*/

    private fun whenCompletedSimple(method: (T?, Throwable?) -> Unit) {
        val listener = object: Listener<T> {
            override fun onResult(result: T) {
                method.invoke(result, null)
            }

            override fun onError(error: Throwable) {
                method.invoke(null, error)
            }
        }
        if (finished()) {
            propagateResult(listener)
        } else {
            listeners.add(listener)
        }
    }

    fun whenCompleted(method: (T?, Throwable?) -> Unit): Future<T> {
        val promise = Future<T>(cancelCallback = cancelCallback)
        val listener = object: Listener<T> {
            override fun onResult(result: T) {
                method.invoke(result, null)
                promise.setResult(result)
            }

            override fun onError(error: Throwable) {
                method.invoke(null, error)
                promise.setError(error)
            }
        }
        if (finished()) {
            propagateResult(listener)
        } else {
            listeners.add(listener)
        }
        return promise
    }
}

suspend fun <T> Future<T>.await() = suspendCoroutine<T> { cont ->
    whenCompleted({value, exception ->
        if (exception != null)
            cont.resumeWithException(exception)
        else // cast to T in case T is nullable and is actually null
            cont.resume(value as T)
    })
}

fun <T> async(block: suspend () -> T): Future<T> {
    val future = Future<T>()

    block.startCoroutine(object : Continuation<T> {
        override val context: CoroutineContext get() = EmptyCoroutineContext
        override fun resume(value: T) { future.setResult(value) }
        override fun resumeWithException(exception: Throwable) { future.setError(exception) }
    })
    return future
}
