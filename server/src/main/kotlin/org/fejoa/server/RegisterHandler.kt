package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.AccountIO
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.RegisterJob
import org.fejoa.network.ReturnType
import org.fejoa.platformGetAccountIO
import java.io.*

class RegisterHandler : JsonRequestHandler(RegisterJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) = runBlocking {

        val request = JsonRPCRequest.parse(RegisterJob.Params::class.serializer(), json)
        val params = request.params

        val accountIO = platformGetAccountIO(AccountIO.Type.SERVER, session.baseDir, params.user)
        if (accountIO.exists()) {
            val response = request.makeError(ReturnType.ERROR, "Account exists")
            return@runBlocking responseHandler.setResponseHeader(response)
        }

        accountIO.writeLoginData(params.loginParams)
        params.userDataConfig?.let {
            accountIO.writeUserDataConfig(it)
        }

        // add the freshly registered user as authenticated
        session.getServerAccessManager().addAccountAccess(params.user)

        val response = request.makeResponse("User ${params.user} registered").stringify(StringSerializer)
        responseHandler.setResponseHeader(response)
    }
}