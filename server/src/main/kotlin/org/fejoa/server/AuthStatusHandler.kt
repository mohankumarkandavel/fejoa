package org.fejoa.server

import kotlinx.serialization.json.JSON
import kotlinx.serialization.serializer
import org.fejoa.network.AuthStatusJob
import org.fejoa.network.JsonRPCSimpleRequest
import java.io.InputStream


class AuthStatusHandler : JsonRequestHandler(AuthStatusJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) {

        val request = JSON.Companion.parse<JsonRPCSimpleRequest>(json)

        val authenticatedAccounts = session.getServerAccessManager().authenticatedAccounts.map { it }
        val response = request.makeResponse(AuthStatusJob.Response(authenticatedAccounts))
                .stringify(AuthStatusJob.Response::class.serializer())
        responseHandler.setResponseHeader(response)
    }
}
