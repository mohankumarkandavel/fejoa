package org.fejoa

import org.fejoa.repository.sync.AccessRight


/**
 * Keeps track about auth status at a remote.
 */
class AccessTracker(val noAccessControl: Boolean = false) {
    // account the user is logged into
    private val authAccounts: MutableSet<String> = HashSet()

    fun getAuthAccounts(): Set<String> {
        return authAccounts
    }

    fun addAccountAccess(user: String) {
        authAccounts.add(user)
    }

    fun removeAccountAccess(user: String) {
        authAccounts.remove(user)
    }

    fun hasAccountAccess(user: String): Boolean {
        return authAccounts.contains(user)
    }

    fun getBranchAccessRights(user: String, branch: String): AccessRight {
        return if (hasAccountAccess(user) || noAccessControl)
            AccessRight.ALL
        else
            TODO()
    }
}
