package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.LoginParams
import org.fejoa.crypto.DH_GROUP
import org.fejoa.crypto.UserKeyParams
import org.fejoa.support.*


class RegisterJob(val user: String, val loginParams: LoginParams) : RemoteJob<RemoteJob.Result>() {

    @Serializable
    class Params(val user: String, val loginParams: LoginParams) {
        constructor(user: String, userKeyParams: UserKeyParams, userKey: BigInteger, group: DH_GROUP)
                : this(user, LoginParams(userKeyParams, group.params.g.modPow(userKey, group.params.p).toString(16), group))
    }

    companion object {
        val METHOD = "register"
    }

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = Params(user, loginParams))
                .stringify(Params.serializer())
    }

    suspend override fun run(remoteRequest: RemoteRequest): Result {
        val reply = remoteRequest.send(getHeader())
        val responseHeader = reply.receiveHeader()

        val response = try {
            JsonRPCResult.parse(StringSerializer, responseHeader, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), responseHeader, id).error
            return Result(ReturnType.ERROR, error.message)
        }

        return Result(ReturnType.OK, response.result)
    }


}