package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.serializer
import org.fejoa.AccountIO
import org.fejoa.crypto.CompactPAKE_SHA256_CTR
import org.fejoa.network.*
import org.fejoa.platformGetAccountIO
import java.io.InputStream


class LoginHandler : JsonRequestHandler(LoginJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) {
        try {
            val initParams = JsonRPCRequest.parse(
                    LoginJob.CompactPAKEInitParams::class.serializer(), json)
            if (initParams.params.type != LoginJob.AuthType.COMPACT_PAKE_INIT)
                throw Exception("Invalid type")
            handleInit(initParams, responseHandler, session)
        } catch (e: Exception) {
            val finishParams = JsonRPCRequest.parse(
                    LoginJob.CompactPAKEFinishParams::class.serializer(), json)
            if (finishParams.params.type != LoginJob.AuthType.COMPACT_PAKE_FINISH)
                throw Exception("Invalid type")
            handleFinish(finishParams, responseHandler, session)
        }

    }

    private fun handleInit(request: JsonRPCRequest<LoginJob.CompactPAKEInitParams>, responseHandler: Portal.ResponseHandler,
                           session: Session) = runBlocking {
        val params = request.params
        session.setLoginCompactPAKEProver(params.user, null)

        val loginData = try {
            platformGetAccountIO(AccountIO.Type.SERVER, session.baseDir, params.user).readLoginData()
        } catch (e: Exception) {
            responseHandler.setResponseHeader(request.makeError(ReturnType.ERROR, "Invalid user"))
            return@runBlocking
        }

        val prover = CompactPAKE_SHA256_CTR.createProver(params.data.group, loginData.getSharedSecret())
        session.setLoginCompactPAKEProver(params.user, prover)

        val encGX = prover.getEncGX()
        val result = LoginJob.CompactPakeInitResponse(loginData.userKeyParams, loginData.group, encGX.first, encGX.second)

        val response = request.makeResponse(result).stringify(LoginJob.CompactPakeInitResponse::class.serializer())
        responseHandler.setResponseHeader(response)
    }

    private fun handleFinish(request: JsonRPCRequest<LoginJob.CompactPAKEFinishParams>,
                             responseHandler: Portal.ResponseHandler, session: Session) = runBlocking {
        val params = request.params
        val prover = session.getLoginCompactPAKEProver(params.user) ?: run {
            responseHandler.setResponseHeader(request.makeError(ReturnType.ERROR,
                    "Invalid authentication session"))
            return@runBlocking
        }
        val state1 = prover.setVerifierResponse(params.data.getEncGY(),
                params.data.getIv(), params.data.getAuthToken()) ?: run {
            responseHandler.setResponseHeader(request.makeError(ReturnType.ERROR,
                    "Failed to authenticate ${params.user}"))
            return@runBlocking
        }

        session.setLoginCompactPAKEProver(params.user, null)
        session.getServerAccessManager().addAccountAccess(params.user)

        val response = request.makeResponse(LoginJob.CompactPakeFinishResponse(state1.getAuthToken()))
                .stringify(LoginJob.CompactPakeFinishResponse::class.serializer())
        responseHandler.setResponseHeader(response)
    }
}