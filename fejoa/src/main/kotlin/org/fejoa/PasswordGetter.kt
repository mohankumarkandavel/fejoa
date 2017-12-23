package org.fejoa


interface PasswordGetter {
    enum class Purpose {
        SERVER_LOGIN,
        OPEN_ACCOUNT,
        OTHER
    }

    /**
     * Gets a password
     *
     * @param purpose e.g. "login", "open account"
     * @param resource the resource the password is need for, e.g. user@server.org, UserData(namespace)
     * @param info some more info about required password
     *
     * @return if null the password request has been declient, e.g. canceled by the user
     */
    suspend fun get(purpose: Purpose, resource: String = "", info: String = ""): String?
}

/**
 * Default password getter
 */
class CanceledPasswordGetter : PasswordGetter {
    override suspend fun get(purpose: PasswordGetter.Purpose, resource: String, info: String): String? {
        return null
    }
}


class SinglePasswordGetter(private val password: String) : PasswordGetter {
    suspend override fun get(purpose: PasswordGetter.Purpose, resource: String, info: String): String? {
        return password
    }
}