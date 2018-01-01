package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.fejoa.repository.Repository
import org.fejoa.repository.sync.PushRequest
import org.fejoa.repository.sync.Request
import org.fejoa.repository.sync.Request.BRANCH_REQUEST_METHOD
import org.fejoa.storage.Hash


@Serializable
class StorageRPCParams(val user: String, val branch: String)

class PushJob(val repository: Repository, val user: String, val branch: String) : RemoteJob<PushJob.Result>() {

    class Result(code: ReturnType, message: String, val result: Request.ResultType, val head: Hash)
        : RemoteJob.Result(code, message)

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = BRANCH_REQUEST_METHOD, params = StorageRPCParams(user, branch))
                .stringify(StorageRPCParams::class.serializer())
    }

    override suspend fun run(remoteRequest: RemoteRequest): Result {
        val head = repository.getHead()
        val pushRequest = PushRequest(repository)
        val pipe = RemotePipeImpl(getHeader(), remoteRequest, null)
        val result = pushRequest.push(pipe, repository.getCurrentTransaction(), branch)

        return Result(ReturnType.OK, "ok", result, head)
    }
}
