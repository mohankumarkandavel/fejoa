package org.fejoa.server

import org.fejoa.BranchIndex
import org.fejoa.FejoaContext
import org.fejoa.crypto.CryptoHelper
import org.fejoa.storage.HashValue
import org.fejoa.support.await


class ServerBranchIndex(val user: String, val context: FejoaContext) {
    private var branchIndexVar: BranchIndex? = null

    suspend fun getBranchIndex(): BranchIndex {
        return UserThreadContext.run(user) {
            getBranchIndexUnlocked()
        }
    }

    suspend fun updateBranch(branch: String, tip: HashValue) {
        UserThreadContext.run(user) {
            updateBranchesUnlocked(listOf(branch to tip))
        }
    }

    private val branchIndexDir = user + "/branchIndex"

    private suspend fun getBranchIndexUnlocked(): BranchIndex {

        val platformStorage = context.platformStorage
        if (branchIndexVar == null) {
            val branches = platformStorage.listBranches(branchIndexDir)
            val remoteIndexName = branches.firstOrNull()
            val branchName = if (remoteIndexName == null)
                CryptoHelper.generateSha256Id().toHex()
            else
                remoteIndexName.getBranchName()

            val storageDir = context.getStorage(branchName, null)
            branchIndexVar = BranchIndex(storageDir)

            if (remoteIndexName == null)
                updateAllBranchesUnlocked()
        }
        return branchIndexVar!!
    }

    private suspend fun updateAllBranchesUnlocked() {
        val localBranches = context.platformStorage.listBranches(branchIndexDir)
        val branchTips = localBranches
                .map {
                    val head = it.getHead().await() ?: return@map null
                    Pair(it.getBranchName(), head.entryId)
                }.filterNotNull()

        updateBranchesUnlocked(branchTips)
    }

    private suspend fun updateBranchesUnlocked(branches: List<Pair<String, HashValue>>) {
        val branchIndex = getBranchIndexUnlocked()
        for ((first, second) in branches)
            branchIndex.update(first, second)
        branchIndex.commit()
    }
}