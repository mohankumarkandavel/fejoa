package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import kotlinx.serialization.serializer
import org.fejoa.repository.sync.AccessRight


class AuthStatusJob() : RemoteJob<AuthStatusJob.Result>() {
    companion object {
        val METHOD = "authStatus"
    }

    class Result(code: ReturnType, message: String, val accounts: List<String> = ArrayList())
        : RemoteJob.Result(code, message)

    @Serializable
    class Response(val accounts: List<String>)

    private fun getHeader(): String {
        return JSON.stringify(JsonRPCSimpleRequest(id = id, method = METHOD))
    }

    override suspend fun run(remoteRequest: RemoteRequest): Result {
        val replyString = remoteRequest.send(getHeader()).receiveHeader()

        val response = try {
            JsonRPCResult.parse(Response::class.serializer(), replyString, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), replyString, id).error
            return Result(ensureError(error.code), error.message)
        }

        return Result(ReturnType.OK, "Auth status received", response.result.accounts)
    }
}