package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.serializer
import org.fejoa.AccountIO
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.RetrieveUserDataConfigJob
import org.fejoa.network.ReturnType
import org.fejoa.platformGetAccountIO
import java.io.InputStream


class RetrieveUserDataConfigHandler : JsonRequestHandler(RetrieveUserDataConfigJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) = runBlocking {

        val request = JsonRPCRequest.parse(RetrieveUserDataConfigJob.RPCParams::class.serializer(), json)
        val params = request.params

        if (!session.getServerAccessManager().hasAccountAccess(params.user)) {
            val response = request.makeError(ReturnType.ACCESS_DENIED, "User ${params.user} not authenticated")
            return@runBlocking responseHandler.setResponseHeader(response)
        }

        val userDataConfig = platformGetAccountIO(AccountIO.Type.SERVER, session.baseDir, params.user).readUserDataConfig()

        val response = request.makeResponse(RetrieveUserDataConfigJob.PPCResult(userDataConfig))
                .stringify(RetrieveUserDataConfigJob.PPCResult::class.serializer())
        responseHandler.setResponseHeader(response)
    }
}