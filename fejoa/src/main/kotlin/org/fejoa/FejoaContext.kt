package org.fejoa

import org.fejoa.chunkcontainer.BoxSpec
import org.fejoa.storage.HashSpec
import org.fejoa.crypto.BaseKeyCache
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.SymBaseCredentials
import org.fejoa.repository.*
import org.fejoa.storage.StorageDir
import org.fejoa.storage.platformCreateStorage
import org.fejoa.support.Executor
import org.fejoa.support.await


class FejoaContext(val accountType: AccountIO.Type, val context: String, val namespace: String, val executor: Executor) {
    var passwordGetter: PasswordGetter = CanceledPasswordGetter()
    val baseKeyCache by lazy { BaseKeyCache() }
    val accountIO by lazy { platformGetAccountIO(accountType, context, namespace) }
    val platformStorage by lazy { platformCreateStorage(context) }

    suspend fun getStorage(branch: String, symCredentials: SymBaseCredentials?, commitSignature: CommitSignature? = null,
                           ref: RepositoryRef? = null) : StorageDir {
        val storage = platformCreateStorage(context).let {
            if (it.exists(namespace, branch)) {
                val backend = it.open(namespace, branch)
                val repositoryRef = ref ?: let {
                    val branchLogIO = getBranchLogIO(symCredentials)
                    val headMessage = backend.getBranchLog().getHead().await()?.message
                            ?: throw Exception("Fail to read branch head")
                    branchLogIO.readFromLog(headMessage)
                }
                val repo = Repository.open(branch, repositoryRef, backend, symCredentials)
                val headCommit = repo.getHeadCommit()
                if (headCommit != null && commitSignature != null) {
                    if (headCommit.verify(commitSignature))
                        throw Exception("Failed to verify commit")
                }
                repo
            } else {
                val backend = it.create(namespace, branch)
                Repository.create(branch, backend, getRepoConfig(symCredentials), symCredentials)
            }
        }

        return StorageDir(storage, "", commitSignature, executor)
    }

    private fun getBranchLogIO(credentials: SymBaseCredentials?): BranchLogIO {
        return if (credentials == null)
            RepositoryBuilder.getPlainBranchLogIO()
        else
            RepositoryBuilder.getEncryptedBranchLogIO(credentials.key, credentials.settings)
    }

    private fun getRepoConfig(symCredentials: SymBaseCredentials?): RepositoryConfig {
        val seed = CryptoHelper.crypto.generateSalt16()
        val hashSpec = HashSpec.createCyclicPoly(HashSpec.HashType.FEJOA_CYCLIC_POLY_2KB_8KB, seed)

        val cryptoType = if (symCredentials != null)
            BoxSpec.EncryptionInfo.Type.PARENT
        else
            BoxSpec.EncryptionInfo.Type.PLAIN

        val boxSpec = BoxSpec(
                encInfo = BoxSpec.EncryptionInfo(cryptoType),
                zipType = BoxSpec.ZipType.DEFLATE,
                zipBeforeEnc = true
        )

        return RepositoryConfig(
                hashSpec = hashSpec,
                boxSpec = boxSpec
        )
    }
}