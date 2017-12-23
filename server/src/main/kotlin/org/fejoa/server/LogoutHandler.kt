package org.fejoa.server

import kotlinx.serialization.serializer
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.LogoutJob
import java.io.InputStream


class LogoutHandler : JsonRequestHandler(LogoutJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) {

        val request = JsonRPCRequest.parse(LogoutJob.Params::class.serializer(), json)

        val accessManager = session.getServerAccessManager()
        request.params.users.forEach {
            accessManager.removeAccountAccess(it)
        }

        val authenticatedAccounts = session.getServerAccessManager().getAuthAccounts().map { it }
        val response = request.makeResponse(LogoutJob.Response(authenticatedAccounts))
                .stringify(LogoutJob.Response::class.serializer())
        responseHandler.setResponseHeader(response)
    }
}