package org.fejoa.network

import kotlinx.serialization.Serializable
import org.fejoa.AuthParams
import org.fejoa.auth.crypto.DH_GROUP
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.SecretKey
import org.fejoa.crypto.UserKeyParams
import org.fejoa.support.*


suspend fun SecretKey.toBigInteger(): BigInteger {
    val raw = CryptoHelper.crypto.encode(this).await().toHex()
    return BigInteger(raw, 16)
}


class RegisterJob(val user: String, val authParams: AuthParams)
    : SimpleRemoteJob<RemoteJob.Result>(false) {


    @Serializable
    class Params(val user: String, val authParams: AuthParams) {
        constructor(user: String, userKeyParams: UserKeyParams, userKey: BigInteger, group: DH_GROUP)
                : this(user, AuthParams(userKeyParams, group.params.g.modPow(userKey, group.params.p).toString(16), group))
    }

    companion object {
        val METHOD = "register"
    }

    override fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = Params(user, authParams))
                .stringify(Params.serializer())
    }

    suspend override fun handle(responseHeader: String, inStream: AsyncInStream): Result {
        val response = JsonRPCResponse.parse<JsonRPCStatusResult>(JsonRPCStatusResult.serializer(),
                responseHeader, id)

        val params = response.result
        return Result(params.status, params.message)
    }
}