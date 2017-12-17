package org.fejoa

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import org.fejoa.crypto.*
import org.fejoa.support.*


@Serializable
class UserDataRef(val branch: String, val settings: CryptoSettings.Symmetric)


/**
 * Data required by the server
 */
@Serializable
class ServerData(val outQueue: String, val inQueue: String, val accessStore: String)

/**
 * Information to access UserData
 *
 * Contains the UserKeyParams to derive the master key which is used to decrypt the UserDataIndex and the KeyStore. The
 * UserData can be opened using credentials stored in the KeyStore.
 *
 * @param encMasterKey to derive the master key
 * @param userData TODO
 * @param extra unencrypted data
 */
@Serializable
class UserDataSettings(val encMasterKey: PasswordProtectedKey,
                       val userData: EncData, val serverData: ServerData) {
    companion object {
        suspend fun create(masterKey: SecretKey, password: String, userKeyParams: UserKeyParams,
                           userData: UserDataRef,
                           outQueue: String, inQueue: String, accessStore: String,
                           cache: BaseKeyCache): UserDataSettings {
            // encrypt the master key
            val encMasterKey = PasswordProtectedKey.create(masterKey, userKeyParams, password, cache)

            // enc user data
            val settings = CryptoSettings.default.symmetric
            val iv = CryptoHelper.crypto.generateInitializationVector(settings.ivSize)
            val data = JSON(indented = true).stringify(userData).toUTF()
            val encrypted = CryptoHelper.crypto.encryptSymmetric(data, masterKey, iv, settings).await()

            return UserDataSettings(encMasterKey, EncData(encrypted, iv, settings),
                    ServerData(outQueue, inQueue, accessStore))
        }
    }

    suspend fun open(password: String, cache: BaseKeyCache): Pair<SymBaseCredentials, UserDataRef> {
        val masterKey = encMasterKey.decryptKey(password, cache)
        val userDataRef = getUserDataRef(masterKey, userData)
        return SymBaseCredentials(masterKey, userDataRef.settings) to userDataRef
    }

    suspend private fun getUserDataRef(key: SecretKey, encData: EncData): UserDataRef {
        val plain = CryptoHelper.crypto.decryptSymmetric(encData.getEncData(), key, encData.getIv(),
                encData.settings).await()
        return JSON.parse(plain.toUTFString())
    }
}

expect fun platformWriteUserDataSettings(namespace: String, userDataSettings: UserDataSettings)
expect fun platformReadUserDataSettings(namespace: String): UserDataSettings