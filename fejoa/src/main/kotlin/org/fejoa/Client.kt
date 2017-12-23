package org.fejoa

import org.fejoa.crypto.*
import org.fejoa.network.*
import org.fejoa.support.Executor
import org.fejoa.support.NowExecutor



class Client(val userData: UserData,
             val connectionAuthManager: ConnectionAuthManager = ConnectionAuthManager(userData.context)) {
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
            val remote = Remote("no_id", user, url)
            val connectionAuthManager = ConnectionAuthManager(context)

            val reply = connectionAuthManager.send(RetrieveUserDataConfigJob(user),
                    remote, LoginAuthInfo(), passwordGetter)
            if (reply.code != ReturnType.OK)
                throw Exception(reply.message)
            val userDataConfig = reply.userDataConfig ?: throw Exception("Missing user data config")

            context.accountIO.writeUserDataConfig(userDataConfig)
            // TODO pull user data

            val openPassword = passwordGetter.get(PasswordGetter.Purpose.OPEN_ACCOUNT,
                    "$namespace/$user")
            ?: throw Exception("Canceled by user")
            return open(baseContext, namespace, openPassword, executor)
        }
    }

    /**
     * Registers an account at a remote server
     */
    suspend fun registerAccount(user: String, url: String, password: String, userKeyParams: UserKeyParams? = null)
        : RemoteJob.Result {

        val params = userKeyParams ?: UserKeyParams(
                BaseKeyParams(salt = CryptoHelper.crypto.generateSalt16(), kdf = CryptoSettings.default.kdf),
                CryptoSettings.HASH_TYPE.SHA256, CryptoSettings.KEY_TYPE.AES, CryptoHelper.crypto.generateSalt16())

        val loginSecret = userData.context.baseKeyCache.getUserKey(params, password)
        val loginParams = LoginParams(params,
                CompactPAKE_SHA256_CTR.getSharedSecret(DH_GROUP.RFC5114_2048_256, loginSecret),
                DH_GROUP.RFC5114_2048_256)

        val request = platformCreateHTMLRequest(url)
        return RegisterJob(user, loginParams, userData.getUserDataSettings(password, params)).run(request)
    }
}