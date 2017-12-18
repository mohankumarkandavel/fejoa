package org.fejoa.server

import kotlinx.serialization.serializer
import org.fejoa.network.Errors
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.RegisterJob
import org.fejoa.platformWriteAuthData
import java.io.*

class RegisterHandler : JsonRequestHandler(RegisterJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) {

        val request = JsonRPCRequest.parse(RegisterJob.Params::class.serializer(), json)
        val params = request.params

        platformWriteAuthData(session.baseDir, params.user, params.authParams)

        val response = request.makeResponse(Errors.OK, "User ${params.user} registered")
        responseHandler.setResponseHeader(response)
    }
}