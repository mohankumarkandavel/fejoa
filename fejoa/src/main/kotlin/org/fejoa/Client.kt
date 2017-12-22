package org.fejoa

import org.fejoa.crypto.*
import org.fejoa.network.*
import org.fejoa.support.Executor
import org.fejoa.support.NowExecutor


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
     */
    suspend fun get(purpose: Purpose, resource: String = "", info: String = ""): String
}


class Client(val userData: UserData) {
    companion object {
        suspend fun create(baseContext: String, namespace: String, password: String, kdf: CryptoSettings.KDF = CryptoSettings.default.kdf,
                           executor: Executor = NowExecutor()): Client {
            val userKeyParams = UserKeyParams(BaseKeyParams(kdf, CryptoHelper.crypto.generateSalt16()),
                    CryptoSettings.HASH_TYPE.SHA256, CryptoSettings.KEY_TYPE.AES, CryptoHelper.crypto.generateSalt16())

            val context = FejoaContext(AccountIO.Type.CLIENT, baseContext, namespace, executor)
            val userData = UserData.create(context, CryptoSettings.default)
            val userDataSettings = userData.getUserDataSettings(password, userKeyParams)

            val accountIO = context.accountIO
            if (accountIO.exists())
                throw Exception("Account exisits")

            accountIO.writeUserDataConfig(userDataSettings)

            return Client(userData)
        }

        suspend fun open(baseContext: String, namespace: String, password: String, executor: Executor = NowExecutor()): Client {
            val context = FejoaContext(AccountIO.Type.CLIENT, baseContext, namespace, executor)

            val platformIO = platformGetAccountIO(AccountIO.Type.CLIENT, baseContext, namespace)

            val userDataSettings = platformIO.readUserDataConfig()
            val openedSettings = userDataSettings.open(password, context.baseKeyCache)

            val userData = UserData.open(context, openedSettings.first, openedSettings.second.branch)
            return Client(userData)
        }

        suspend fun retrieveAccount(baseContext: String, namespace: String, url: String, user: String, passwordGetter: PasswordGetter,
                                    executor: Executor = NowExecutor()): Client {
            val context = FejoaContext(AccountIO.Type.CLIENT, baseContext, namespace, executor)
            val password =  passwordGetter.get(PasswordGetter.Purpose.SERVER_LOGIN, "$user@$url")
            // TODO use a connection manager
            val request = platformCreateHTMLRequest(url)
            val reply = LoginJob(user, password, context.baseKeyCache).run(request)
            if (reply.code != ReturnType.OK)
                throw Exception(reply.message)

            // TODO use a connection manager
            val userDataConfigReply = RetrieveUserDataConfigJob(user).run(request)
            if (userDataConfigReply.code != ReturnType.OK)
                throw Exception(reply.message)
            val userDataConfig = userDataConfigReply.userDataConfig
                    ?: throw Exception("Missing user data config")

            context.accountIO.writeUserDataConfig(userDataConfig)
            // TODO pull user data

            return open(baseContext, namespace, passwordGetter.get(PasswordGetter.Purpose.OPEN_ACCOUNT,
                    "$namespace/$user"), executor)

        }
    }

    /**
     * Registers an account at a remote server
     */
    suspend fun registerAccount(url: String, user: String, password: String, userKeyParams: UserKeyParams? = null)
        : RemoteJob.Result {
        // TODO use a connection manager
        val request = platformCreateHTMLRequest(url)
        val params = userKeyParams ?: UserKeyParams(
                BaseKeyParams(salt = CryptoHelper.crypto.generateSalt16(), kdf = CryptoSettings.default.kdf),
                CryptoSettings.HASH_TYPE.SHA256, CryptoSettings.KEY_TYPE.AES, CryptoHelper.crypto.generateSalt16())

        val loginSecret = userData.context.baseKeyCache.getUserKey(params, password)
        val loginParams = LoginParams(params,
                CompactPAKE_SHA256_CTR.getSharedSecret(DH_GROUP.RFC5114_2048_256, loginSecret),
                DH_GROUP.RFC5114_2048_256)
        return RegisterJob(user, loginParams, userData.getUserDataSettings(password, params)).run(request)
    }
}