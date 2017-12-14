package org.fejoa

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import org.fejoa.crypto.*
import org.fejoa.support.*


@Serializable
class KeyStoreRef(val branch: String, val settings: CryptoSettings, val iv: String)

/**
 * Information about UserData branch names
 *
 * @param keyStore ref to the key store
 * @param userData user data branch name
 */
@Serializable
class UserDataIndex(val keyStore: KeyStoreRef, val userData: String)

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
 * @param protectedMasterKey to derive the master key
 * @param settings crypto settings for the index and the key store
 * @param data base64 encoded, encrypted UserDataIndex
 * @param extra unencrypted data
 */
@Serializable
class UserDataSettings(val protectedMasterKey: PasswordProtectedKey,
                       val settings: CryptoSettings.Symmetric, val iv: String, val data: String, val extra: ServerData) {
    companion object {
        suspend fun create(masterKey: SecretKey, password: String, userKeyParams: UserKeyParams,
                           index: UserDataIndex,
                           outQueue: String, inQueue: String, accessStore: String,
                           cache: BaseKeyCache): UserDataSettings {

            val data = JSON(indented = true).stringify(index).toUTF()
            val protectedMasterKey = PasswordProtectedKey.create(masterKey, userKeyParams, password, cache)
            val settings = CryptoSettings.default.symmetric
            val iv = CryptoHelper.crypto.generateInitializationVector(settings.ivSize)
            val encrypted = CryptoHelper.crypto.encryptSymmetric(data, masterKey, iv, settings).await()

            return UserDataSettings(protectedMasterKey, settings, iv.encodeBase64(),
                    encrypted.encodeBase64(), ServerData(outQueue, inQueue, accessStore))
        }
    }

    suspend fun getIndex(symCredentials: SymCredentials): UserDataIndex {
        val plain = CryptoHelper.crypto.decryptSymmetric(data.decodeBase64(), symCredentials).await()
        return JSON.parse(plain.toUTFString())
    }
}

expect fun platformWriteUserDataSettings(namespace: String, userDataSettings: UserDataSettings)
expect fun platformReadUserDataSettings(namespace: String): UserDataSettings