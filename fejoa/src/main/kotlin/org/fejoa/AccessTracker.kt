package org.fejoa


/**
 * Keeps track about auth status at a remote.
 */
class AccessTracker {
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
}
