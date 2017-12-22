package org.fejoa.network

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.LoginParams
import org.fejoa.UserDataConfig
import org.fejoa.crypto.DH_GROUP
import org.fejoa.crypto.UserKeyParams
import org.fejoa.support.*


class RegisterJob(val user: String, val loginParams: LoginParams, val userDataConfig: UserDataConfig? = null)
    : RemoteJob<RemoteJob.Result>() {

    @Serializable
    class Params(val user: String, val loginParams: LoginParams, @Optional val userDataConfig: UserDataConfig? = null) {
        constructor(user: String, userKeyParams: UserKeyParams, userKey: BigInteger, group: DH_GROUP,
                    userDataConfig: UserDataConfig?) : this(user,
                LoginParams(userKeyParams, group.params.g.modPow(userKey, group.params.p).toString(16), group),
                userDataConfig)
    }

    companion object {
        val METHOD = "register"
    }

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = Params(user, loginParams, userDataConfig))
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