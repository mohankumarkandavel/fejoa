package org.fejoa.server

import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.RegisterJob
import org.fejoa.platformWriteLoginData
import java.io.*

class RegisterHandler : JsonRequestHandler(RegisterJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) {

        val request = JsonRPCRequest.parse(RegisterJob.Params::class.serializer(), json)
        val params = request.params

        platformWriteLoginData(session.baseDir, params.user, params.loginParams)

        val response = request.makeResponse("User ${params.user} registered").stringify(StringSerializer)
        responseHandler.setResponseHeader(response)
    }
}