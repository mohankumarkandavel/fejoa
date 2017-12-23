package org.fejoa.server

import org.fejoa.AccessTracker
import org.fejoa.crypto.CompactPAKE_SHA256_CTR
import javax.servlet.http.HttpSession


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

    fun getServerAccessManager(): AccessTracker {
        return session.getAttribute(ROLES_KEY)?.let { it as AccessTracker }
                ?: return AccessTracker().also { session.setAttribute(ROLES_KEY, it) }
    }

}
