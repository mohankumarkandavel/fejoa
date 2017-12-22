package org.fejoa.network

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.fejoa.UserDataConfig

class RetrieveUserDataConfigJob(val user: String) : RemoteJob<RetrieveUserDataConfigJob.Result>() {
    companion object {
        val METHOD = "retrieveUserDataConfig"
    }

    @Serializable
    class RPCParams(val user: String)

    /**
     * List of accounts that are still active (logged in)
     */
    @Serializable
    class PPCResult(@Optional val userDataConfig: UserDataConfig? = null)

    class Result(code: ReturnType, message: String, val userDataConfig: UserDataConfig? = null)
        : RemoteJob.Result(code, message)

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = RPCParams(user)).stringify(RPCParams::class.serializer())
    }

    override suspend fun run(remoteRequest: RemoteRequest): Result {
        val replyString = remoteRequest.send(getHeader()).receiveHeader()

        val response = try {
            JsonRPCResult.parse(PPCResult::class.serializer(), replyString, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), replyString, id).error
            return Result(ensureError(error.code), error.message)
        }

        return Result(ReturnType.OK, "ok", response.result.userDataConfig)
    }
}
