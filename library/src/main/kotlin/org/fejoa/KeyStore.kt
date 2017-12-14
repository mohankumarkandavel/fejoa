package org.fejoa

import org.fejoa.crypto.*
import org.fejoa.storage.*


class KeyStore(storageDir: StorageDir, val masterCredentials: SymCredentials) : StorageDirObject(storageDir) {
    val symCredentialList = SymCredentialList(storageDir, "symCredentials")

    companion object {
        suspend fun create(context: FejoaContext, settings: CryptoSettings.Symmetric)
                : KeyStore {
            val branchName = CryptoHelper.generateSha256Id()
            val symCredentials = settings.generateCredentials()
            val storage = context.getStorage(branchName.toHex(), symCredentials)
            return KeyStore(storage, symCredentials)
        }

        suspend fun open(context: FejoaContext, branch: String, credentials: SymCredentials): KeyStore {
            val storage = context.getStorage(branch, credentials)
            return KeyStore(storage, credentials)
        }
    }

    suspend fun addBranchCredentials(branch: HashValue, credentials: SymCredentials) {
        symCredentialList.get(branch).write(credentials)
    }

    suspend fun getBranchCredentials(branch: HashValue): SymCredentials? {
        return symCredentialList.get(branch).get()
    }
}