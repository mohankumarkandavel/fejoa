package org.fejoa.network

import kotlinx.serialization.serializer
import org.fejoa.repository.CommitSignature
import org.fejoa.repository.Repository
import org.fejoa.repository.sync.PullRequest
import org.fejoa.repository.sync.Request.BRANCH_REQUEST_METHOD
import org.fejoa.storage.Hash
import org.fejoa.storage.MergeStrategy


class PullJob(val repository: Repository, val signature: CommitSignature?, val mergeStrategy: MergeStrategy, val user: String, val branch: String) : RemoteJob<PullJob.Result>() {

    class Result(code: ReturnType, message: String, val oldHead: Hash, val remoteHead: Hash?)
        : RemoteJob.Result(code, message)

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = BRANCH_REQUEST_METHOD, params = StorageRPCParams(user, branch))
                .stringify(StorageRPCParams::class.serializer())
    }

    override suspend fun run(remoteRequest: RemoteRequest): Result {
        val oldHead = repository.getHead()
        val pushRequest = PullRequest(repository, signature)
        val pipe = RemotePipeImpl(getHeader(), remoteRequest, null)
        val result = pushRequest.pull(pipe, branch, mergeStrategy)
                ?: return Result(ReturnType.ERROR, "failed to pull", oldHead, null)

        return Result(ReturnType.OK, "ok", oldHead, result)
    }
}