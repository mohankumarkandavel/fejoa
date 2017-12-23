package org.fejoa

import org.fejoa.network.*


class ConnectionAuthManager(val context: FejoaContext) {
    private val remoteAccessTrackerMap: MutableMap<String, AccessTracker> = HashMap()

    private fun getAccessTracker(remote: Remote): AccessTracker {
        return remoteAccessTrackerMap[remote.id]
                ?: AccessTracker().also { remoteAccessTrackerMap[remote.id] = it }
    }

    private fun getRequest(remote: Remote): RemoteRequest {
        return platformCreateHTMLRequest(remote.server)
    }

    private suspend fun ensureAccess(remote: Remote, authInfo: AuthInfo, pwGetter: PasswordGetter) {
        val accessTracker = getAccessTracker(remote)
        return when (authInfo.type) {
            AuthType.PLAIN -> {}
            AuthType.LOGIN -> {
                if (accessTracker.hasAccountAccess(remote.user))
                    return

                val password = pwGetter.get(PasswordGetter.Purpose.SERVER_LOGIN, remote.server,
                        "ConnectionAuthManager") ?: throw Exception("Login canceled by user")
                val result = LoginJob(remote.user, password, context.baseKeyCache).run(getRequest(remote))
                if (result.code != ReturnType.OK)
                    throw Exception("Login failed: ${result.message}")

                accessTracker.addAccountAccess(remote.user)
                return
            }
        }
    }

    suspend fun <T: RemoteJob.Result>send(job: RemoteJob<T>, remote: Remote, authInfo: AuthInfo,
                                          pwGetter: PasswordGetter? = null): T {
        ensureAccess(remote, authInfo, pwGetter ?: context.passwordGetter)
        val result = job.run(getRequest(remote))
        if (result.code == ReturnType.ACCESS_DENIED) {
            // remove access from tacker
            val accessTracker = getAccessTracker(remote)
            val dummy = when (authInfo.type) {
                AuthType.PLAIN -> throw Exception("Unexpected")
                AuthType.LOGIN -> {
                    accessTracker.removeAccountAccess(remote.user)
                }
            }
        }
        return result
    }
}
