package org.fejoa


/**
 * @param context e.g. the base storage directory
 * @param namespace e.g. the sub directory
 */
expect fun platformGetAccountIO(type: AccountIO.Type, context: String, namespace: String): AccountIO

interface AccountIO {
    enum class Type {
        SERVER,
        CLIENT
    }

    /**
     * @return true if the account exists
     */
    suspend fun exists(): Boolean

    suspend fun writeLoginData(loginData: LoginParams)
    suspend fun readLoginData(): LoginParams

    suspend fun writeUserDataConfig(userDataConfig: UserDataConfig)
    suspend fun readUserDataConfig(): UserDataConfig
}