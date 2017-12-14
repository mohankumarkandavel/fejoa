package org.fejoa

import org.fejoa.chunkcontainer.BoxSpec
import org.fejoa.chunkcontainer.HashSpec
import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.SymCredentials
import org.fejoa.repository.*
import org.fejoa.storage.StorageDir
import org.fejoa.storage.platformCreateStorage
import org.fejoa.support.Executor
import org.fejoa.support.await


class FejoaContext(val namespace: String, val executor: Executor) {
    suspend fun getStorage(branch: String, symCredentials: SymCredentials?, commitSignature: CommitSignature? = null,
                           ref: RepositoryRef? = null) : StorageDir {
        val storage = platformCreateStorage().let {
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
                Repository.create(branch, backend, getRepoConfig(), symCredentials)
            }
        }

        return StorageDir(storage, "", commitSignature, executor)
    }

    private fun getBranchLogIO(credentials: SymCredentials?): BranchLogIO {
        return if (credentials == null)
            RepositoryBuilder.getPlainBranchLogIO()
        else
            RepositoryBuilder.getEncryptedBranchLogIO(credentials.key, credentials.symmetric)
    }

    private fun getRepoConfig(): RepositoryConfig {
        val seed = CryptoHelper.crypto.generateSalt16()
        val hashSpec = HashSpec.createCyclicPoly(HashSpec.HashType.FEJOA_CYCLIC_POLY_2KB_8KB, seed)

        val boxSpec = BoxSpec(
                encInfo = BoxSpec.EncryptionInfo(BoxSpec.EncryptionInfo.Type.PARENT),
                zipType = BoxSpec.ZipType.DEFLATE,
                zipBeforeEnc = true
        )

        return RepositoryConfig(
                hashSpec = hashSpec,
                boxSpec = boxSpec
        )
    }
}