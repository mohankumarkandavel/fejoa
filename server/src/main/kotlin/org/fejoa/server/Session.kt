package org.fejoa.server

import org.fejoa.AccessTracker
import org.fejoa.AccountIO
import org.fejoa.FejoaContext
import org.fejoa.chunkcontainer.BoxSpec
import org.fejoa.crypto.CompactPAKE_SHA256_CTR
import org.fejoa.repository.Repository
import org.fejoa.repository.RepositoryBuilder
import org.fejoa.repository.RepositoryConfig
import org.fejoa.storage.HashSpec
import org.fejoa.storage.StorageDir
import org.fejoa.storage.platformCreateStorage
import org.fejoa.support.NowExecutor
import org.fejoa.support.await
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
                ?: return AccessTracker(DebugSingleton.isNoAccessControl).also { session.setAttribute(ROLES_KEY, it) }
    }

    fun getContext(user: String): FejoaContext {
        return FejoaContext(AccountIO.Type.SERVER, baseDir, user, NowExecutor())
    }

    fun getServerBranchIndex(user: String): ServerBranchIndex {
        return ServerBranchIndex(user, getContext(user))
    }
}
