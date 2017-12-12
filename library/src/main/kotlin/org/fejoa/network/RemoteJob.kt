package org.fejoa.network


abstract class RemoteJob<T : RemoteJob.Result> {
    var followUpJob: RemoteJob<T>? = null
        protected set

    open class Result(val status: Int, val message: String)

    abstract suspend fun run(remoteRequest: RemoteRequest): T

    companion object {
        suspend fun <T : Result> run(job: RemoteJob<T>, remoteRequest: RemoteRequest): T {
            val result = job.run(remoteRequest)
            if (result.status == Errors.FOLLOW_UP_JOB)
                return run(job.followUpJob!!, remoteRequest)

            return result
        }
    }
}
