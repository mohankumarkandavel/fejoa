package org.fejoa.server

import org.fejoa.crypto.CompactPAKE_SHA256_CTR
import org.fejoa.repository.sync.AccessRight
import javax.servlet.http.HttpSession
import kotlin.collections.HashMap


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

    private fun makeRole(serverUser: String, role: String): String {
        return serverUser + ":" + role
    }

    fun addRootRole(userName: String) {
        addRole(userName, "root", AccessRight.ALL)
    }

    fun hasRootRole(serverUser: String): Boolean {
        return getRoles().containsKey(makeRole(serverUser, "root"))
    }


    fun addRole(serverUser: String, role: String, rights: AccessRight) {
        synchronized(getSessionLock()) {
            val roles = getRoles()
            roles.put(makeRole(serverUser, role), rights)
            session.setAttribute(ROLES_KEY, roles)
        }
    }

    fun getRoles(): HashMap<String, AccessRight> {
        return session.getAttribute(ROLES_KEY)?.let { it as HashMap<String, AccessRight>} ?: return HashMap()
    }

    fun getRoleRights(serverUser: String, role: String): AccessRight {
        return getRoles()[makeRole(serverUser, role)] ?: return AccessRight.NONE
    }

    fun setLoginCompactPAKEProver(user: String, prover: CompactPAKE_SHA256_CTR.ProverState0?) {
        session.setAttribute(getLoginCompactPAKEProverKey(user), prover)
    }

    fun getLoginCompactPAKEProver(user: String): CompactPAKE_SHA256_CTR.ProverState0? {
        return session.getAttribute(getLoginCompactPAKEProverKey(user))?.let { it as CompactPAKE_SHA256_CTR.ProverState0}
    }
}
