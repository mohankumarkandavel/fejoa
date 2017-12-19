package org.fejoa

import org.fejoa.crypto.*
import org.fejoa.repository.DefaultCommitSignature
import org.fejoa.storage.*
import org.fejoa.support.await


class User(val storageDir: StorageDir) {
    val id = DBHashValue(storageDir, "id")
    val signingCredentialList = SignCredentialList(storageDir, "signing/credentials")
    val defaultSigningCredential = DBHashValue(storageDir, "signing/default")
}

class UserData private constructor(val context: FejoaContext, val masterKey: SymBaseCredentials, storageDir: StorageDir)
    : StorageDirObject(storageDir) {
    val user = User(storageDir)

    companion object {
        suspend fun create(context: FejoaContext, settings: CryptoSettings = CryptoSettings.default): UserData {
            val signingKeyPair = CryptoHelper.crypto.generateKeyPair(settings.signature.key).await()
            val userId = signingKeyPair.getId()

            val userDataBranch = CryptoHelper.generateSha256Id()
            val userDataCredentials = settings.symmetric.generateBaseCredentials()
            val userDataStorage = context.getStorage(userDataBranch.toHex(), userDataCredentials)
            val userData = UserData(context, userDataCredentials, userDataStorage)

            val signCredentials = SignCredentials(signingKeyPair, settings.signature)
            val signingKeyID = signCredentials.getId()
            userData.user.id.write(signingKeyID)
            userData.user.signingCredentialList.get(signingKeyID).write(signCredentials)
            userData.user.defaultSigningCredential.write(signingKeyID)

            val commitSignature = DefaultCommitSignature(userId, SignCredentials(signingKeyPair, settings.signature),
                    { signer: HashValue, keyId: HashValue -> userData.getSigningKey(signer, keyId)})
            userDataStorage.setCommitSignature(commitSignature)

            return userData
        }

        suspend fun open(context: FejoaContext, masterKey: SymBaseCredentials, branch: String): UserData {
            val userDataStorage = context.getStorage(branch, masterKey)

            val userData = UserData(context, masterKey, userDataStorage)

            val user = userData.user
            val signCredentials = user.signingCredentialList.get(user.defaultSigningCredential.get()).get()
            val commitSignature = DefaultCommitSignature(user.id.get(), signCredentials,
                    { signer: HashValue, keyId: HashValue -> userData.getSigningKey(signer, keyId)})

            userDataStorage.setCommitSignature(commitSignature)

            return userData
        }
    }

    suspend fun getSigningKey(signer: HashValue, keyId: HashValue): PublicKey? {
        if (signer == user.id.get())
            return user.signingCredentialList.get(keyId).get().keyPair.publicKey
        return null
    }

    suspend fun getUserDataSettings(password: String, userKeyParams: UserKeyParams): UserDataSettings {
        return UserDataSettings.create(masterKey.key, password, userKeyParams,
                UserDataRef(branch, masterKey.settings),
                "", "", "",
                context.baseKeyCache)
    }

    override suspend fun commit() {
        super.commit()
    }
}
