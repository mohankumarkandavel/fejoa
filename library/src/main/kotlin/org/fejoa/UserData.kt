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

class UserData private constructor(val context: FejoaContext, storageDir: StorageDir, val keyStore: KeyStore)
    : StorageDirObject(storageDir) {
    val user = User(storageDir)

    companion object {
        suspend fun create(context: FejoaContext, settings: CryptoSettings = CryptoSettings.default): UserData {
            val signingKeyPair = CryptoHelper.crypto.generateKeyPair(settings.signature.key).await()
            val userId = signingKeyPair.getId()

            val keyStore = KeyStore.create(context, settings.symmetric)

            val userDataBranch = CryptoHelper.generateSha256Id()
            val userDataCredentials = settings.symmetric.generateCredentials()
            val userDataStorage = context.getStorage(userDataBranch.toHex(), userDataCredentials)
            val userData = UserData(context, userDataStorage, keyStore)

            val signCredentials = SignCredentials(signingKeyPair, settings.signature)
            val signingKeyID = signCredentials.getId()
            userData.user.id.write(signingKeyID)
            userData.user.signingCredentialList.get(signingKeyID).write(signCredentials)
            userData.user.defaultSigningCredential.write(signingKeyID)

            val commitSignature = DefaultCommitSignature(userId, SignCredentials(signingKeyPair, settings.signature),
                    { signer: HashValue, keyId: HashValue -> userData.getSigningKey(signer, keyId)})
            userDataStorage.setCommitSignature(commitSignature)

            // create key store
            keyStore.storageDir.setCommitSignature(commitSignature)
            keyStore.addBranchCredentials(userDataBranch, userDataCredentials)

            return userData
        }

        suspend fun open(context: FejoaContext, keyStoreBranch: String, masterKey: SymCredentials, branch: String): UserData {
            val keyStore = KeyStore.open(context, keyStoreBranch, masterKey)
            val userDataCredentials = keyStore.getBranchCredentials(HashValue.fromHex(branch))
            val userDataStorage = context.getStorage(branch, userDataCredentials)

            val userData = UserData(context, userDataStorage, keyStore)

            val user = userData.user
            val signCredentials = user.signingCredentialList.get(user.defaultSigningCredential.get()).get()
            val commitSignature = DefaultCommitSignature(user.id.get(), signCredentials,
                    { signer: HashValue, keyId: HashValue -> userData.getSigningKey(signer, keyId)})

            userDataStorage.setCommitSignature(commitSignature)
            keyStore.storageDir.setCommitSignature(commitSignature)

            return userData
        }
    }

    suspend fun getSigningKey(signer: HashValue, keyId: HashValue): PublicKey? {
        if (signer == user.id.get())
            return user.signingCredentialList.get(keyId).get().keyPair.publicKey
        return null
    }

    override suspend fun commit() {
        super.commit()

        keyStore.commit()
    }
}
