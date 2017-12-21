package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer


class LogoutJob(val users: List<String>) : RemoteJob<LogoutJob.Result>() {
    companion object {
        val METHOD = "logout"
    }

    @Serializable
    class Params(val users: List<String>)

    /**
     * List of accounts that are still active (logged in)
     */
    @Serializable
    class Response(val accounts: List<String>)

    class Result(code: ReturnType, message: String, val accounts: List<String> = ArrayList())
        : RemoteJob.Result(code, message)

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = Params(users))
                .stringify(Params.serializer())
    }

    override suspend fun run(remoteRequest: RemoteRequest): Result {
        val replyString = remoteRequest.send(getHeader()).receiveHeader()

        val response = try {
            JsonRPCResult.parse(Response::class.serializer(), replyString, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), replyString, id).error
            return Result(ensureError(error.code), error.message)
        }

        return Result(ReturnType.OK, "ok", response.result.accounts)
    }
}