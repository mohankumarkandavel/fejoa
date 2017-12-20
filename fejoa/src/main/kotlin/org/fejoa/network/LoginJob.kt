package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.fejoa.crypto.BaseKeyCache
import org.fejoa.crypto.CompactPAKE_SHA256_CTR
import org.fejoa.crypto.DH_GROUP
import org.fejoa.crypto.UserKeyParams
import org.fejoa.support.decodeBase64
import org.fejoa.support.encodeBase64


class LoginJob(val user: String, val password: String, val cache: BaseKeyCache) : RemoteJob<RemoteJob.Result>() {
    enum class AuthType {
        COMPACT_PAKE_INIT, // init the auth process
        COMPACT_PAKE_FINISH // finish the auth process
    }


    // login
    @Serializable
    class CompactPAKEInitParams(val user: String = "",
                                val data: CompactPAKEInitData,
                                val type: AuthType = AuthType.COMPACT_PAKE_INIT)

    @Serializable
    class CompactPAKEInitData(val group: DH_GROUP)

    @Serializable
    class CompactPAKEFinishParams(val user: String = "",
                                  val data: CompactPAKEFinishData,
                                  val type: AuthType = AuthType.COMPACT_PAKE_FINISH)

    @Serializable
    class CompactPAKEFinishData(val encGY: String, val iv: String, val authToken: String) {
        constructor(encGY: ByteArray, iv: ByteArray, authToken: ByteArray)
                : this(encGY.encodeBase64(), iv.encodeBase64(), authToken.encodeBase64())


        fun getEncGY(): ByteArray {
            return encGY.decodeBase64()
        }

        fun getIv(): ByteArray {
            return iv.decodeBase64()
        }

        fun getAuthToken(): ByteArray {
            return authToken.decodeBase64()
        }
    }

    /**
     * @param group group to derive the shared secret
     * @param encGX encrypted gx value, stored in base64
     * @param iv iv used for gx encryption, stored in base64
     */
    @Serializable
    class CompactPakeInitResponse(val userKeyParams: UserKeyParams, val group: DH_GROUP, val encGX: String,
                                  val iv: String) {
        constructor(userKeyParams: UserKeyParams, sharedSecretGroup: DH_GROUP, encGX: ByteArray, iv: ByteArray)
                : this(userKeyParams, sharedSecretGroup, encGX.encodeBase64(), iv.encodeBase64())

        fun getEncGX(): ByteArray {
            return encGX.decodeBase64()
        }

        fun getIv(): ByteArray {
            return iv.decodeBase64()
        }
    }

    @Serializable
    class CompactPakeFinishResponse(val authToken: String = "") {
        constructor(authToken: ByteArray)
                : this(authToken.encodeBase64())

        fun getAuthToken(): ByteArray {
            return authToken.decodeBase64()
        }
    }


    companion object {
        val METHOD = "login"
    }

    private fun getGroup(): DH_GROUP {
        return DH_GROUP.RFC5114_2048_256
    }

    private fun getInitRequest(): String {
        return JsonRPCRequest(id = id, method = METHOD,
                params = CompactPAKEInitParams(user, CompactPAKEInitData(getGroup())))
                .stringify(CompactPAKEInitParams::class.serializer())
    }

    suspend private fun getFinishRequest(verifier: CompactPAKE_SHA256_CTR.Verifier): String {
        val encGY = verifier.getEncGy()
        return JsonRPCRequest(id = id, method = METHOD,
                params = CompactPAKEFinishParams(user,
                        CompactPAKEFinishData(encGY.first, encGY.second, verifier.getAuthToken())))
                .stringify(CompactPAKEFinishParams::class.serializer())
    }

    suspend override fun run(remoteRequest: RemoteRequest): Result {
        val initResponseString = remoteRequest.send(getInitRequest()).receiveHeader()
        val initResponse = try {
            JsonRPCResult.parse(CompactPakeInitResponse::class.serializer(), initResponseString, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), initResponseString, id).error
            return Result(ensureError(error.code), error.message)
        }
        val initResult = initResponse.result
        val secret = cache.getUserKey(initResult.userKeyParams, password)
        val sharedSecret = CompactPAKE_SHA256_CTR.getSharedSecret(initResult.group, secret)

        val verifier = CompactPAKE_SHA256_CTR.createVerifier(getGroup(), sharedSecret,
                initResult.getEncGX(), initResult.getIv())

        val finishResponseString = remoteRequest.send(getFinishRequest(verifier)).receiveHeader()
        val finishResponse = try {
            JsonRPCResult.parse(CompactPakeFinishResponse::class.serializer(), finishResponseString, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), finishResponseString, id).error
            return Result(ensureError(error.code), error.message)
        }
        val finishResult = finishResponse.result


        if (!verifier.verify(finishResult.getAuthToken()))
            return Result(ReturnType.ERROR, "Failed to authenticate $user")

        return Result(ReturnType.OK, "User $user authenticated")
    }
}
