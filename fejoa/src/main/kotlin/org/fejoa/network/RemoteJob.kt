package org.fejoa.network


abstract class RemoteJob<T : RemoteJob.Result> {
    companion object {
        private var id: Int = -1
        fun nextId(): Int {
            id++
            return id
        }
    }

    val id: Int

    init {
        id = nextId()
    }

    open class Result(val code: ReturnType, val message: String)

    abstract suspend fun run(remoteRequest: RemoteRequest): T
}
