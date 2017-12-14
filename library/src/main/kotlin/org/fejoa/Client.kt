package org.fejoa

import org.fejoa.crypto.*
import org.fejoa.support.await


class Client(val cache: BaseKeyCache) {
    companion object {
        /*suspend fun create(namespace: String, password: String): Client {
            val cache = BaseKeyCache()

            val settings = CryptoSettings.default.symmetric
            val masterKey = CryptoHelper.crypto.generateSymmetricKey(settings).await()
            val kdfSettings = CryptoSettings.default.kdf
            val userKeyParams = UserKeyParams(BaseKeyParams(kdfSettings, CryptoHelper.crypto.generateSalt16()),
                    CryptoSettings.HASH_TYPE.SHA256, CryptoSettings.KEY_TYPE.AES, CryptoHelper.crypto.generateSalt16())

            val keyStore = KeyStore.create()
            val userDataIndex = UserDataIndex(KeyStoreRef())

            val userDataSettings = UserDataSettings.create(masterKey, password, userKeyParams, userDataIndex, cache)


            return Client()
        }*/
    }
}