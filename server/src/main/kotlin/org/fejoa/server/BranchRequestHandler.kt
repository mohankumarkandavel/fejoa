package org.fejoa.server

import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.AccountIO
import org.fejoa.FejoaContext
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.RPCPipeResponse
import org.fejoa.network.ReturnType
import org.fejoa.network.StorageRPCParams
import org.fejoa.repository.BranchLog
import org.fejoa.repository.sync.AccessRight
import org.fejoa.repository.sync.Request
import org.fejoa.repository.sync.RequestHandler
import org.fejoa.support.NowExecutor
import org.fejoa.support.await
import java.io.IOException
import java.io.InputStream


class BranchRequestHandler : JsonRequestHandler(Request.BRANCH_REQUEST_METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) = runBlocking {
        val request = JsonRPCRequest.parse(StorageRPCParams::class.serializer(), json)
        val params = request.params
        val user = params.user
        val branch = params.branch
        val accessManager = session.getServerAccessManager()

        val branchAccessRights = accessManager.getBranchAccessRights(user, branch)

        val context = FejoaContext(AccountIO.Type.SERVER, session.baseDir, user, NowExecutor())
        val storageBackend = context.platformStorage
        val branchBackend = if (storageBackend.exists(user, branch)) {
            storageBackend.open(user, branch)
        } else if (branchAccessRights.value and AccessRight.PUSH.value != 0){
            storageBackend.create(user, branch)
        } else {
            responseHandler.setResponseHeader(request.makeError(ReturnType.ACCESS_DENIED,
                    "branch access denied"))
            return@runBlocking
        }

        val branchLog = branchBackend.getBranchLog()
        val initialHead = branchLog.getHead().await()

        val transaction = branchBackend.getChunkStorage().startTransaction()
        val handler = RequestHandler(transaction,
                object : RequestHandler.BranchLogGetter {
                    override operator fun get(b: String): BranchLog {
                        if (branch != b)
                            throw IOException("Branch miss match.")
                        return branchLog
                    }
                })

        if (data == null) {
            responseHandler.setResponseHeader(request.makeError(ReturnType.ERROR,
                    "Storage data expected"))
            return@runBlocking
        }

        val pipe = ServerPipe(request.makeResponse(
                RPCPipeResponse("data pipe ok")).stringify(RPCPipeResponse::class.serializer()),
                responseHandler, data)
        val result = handler.handle(pipe, branchAccessRights)
        if (result !== RequestHandler.Result.OK && !responseHandler.isHandled)
            responseHandler.setResponseHeader(request.makeError(ReturnType.ERROR, result.description))
        else if (!responseHandler.isHandled)
            throw Exception("Internal error")

        // update the server branch index if necessary
        val finalHead = branchBackend.getBranchLog().getHead().await() ?: return@runBlocking

        if (initialHead == null || initialHead.entryId != finalHead.entryId) {
            val index = session.getServerBranchIndex(user)
            index.updateBranch(branch, finalHead.entryId)
        }
    }
}