package org.fejoa.server

import org.fejoa.crypto.CompactPAKE_SHA256_CTR
import org.fejoa.repository.sync.AccessRight
import javax.servlet.http.HttpSession
import kotlin.collections.HashMap


class ServerAccessManager {
    // account the user is logged into
    val authenticatedAccounts: MutableSet<String> = HashSet()

    fun hasAccountAccess(user: String): Boolean {
        return authenticatedAccounts.contains(user)
    }
}

class Session(val baseDir: String, private val session: HttpSession) {
    private val ROLES_KEY = "roles"
    private val LOGIN_COMPACTPAKE_PROVER_KEY = "loginCompactPAKEProver"

    private fun getSessionLock(): Any {
        // get an immutable lock
        return session.id.intern()
    }

    private fun getLoginCompactPAKEProverKey(user: String): String {
        return user + ":" + LOGIN_COMPACTPAKE_PROVER_KEY
    }

    fun setLoginCompactPAKEProver(user: String, prover: CompactPAKE_SHA256_CTR.ProverState0?) {
        session.setAttribute(getLoginCompactPAKEProverKey(user), prover)
    }

    fun getLoginCompactPAKEProver(user: String): CompactPAKE_SHA256_CTR.ProverState0? {
        return session.getAttribute(getLoginCompactPAKEProverKey(user))?.let { it as CompactPAKE_SHA256_CTR.ProverState0}
    }

    fun getServerAccessManager(): ServerAccessManager {
        return session.getAttribute(ROLES_KEY)?.let { it as ServerAccessManager}
                ?: return ServerAccessManager().also { session.setAttribute(ROLES_KEY, it) }
    }

}
