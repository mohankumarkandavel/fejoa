package org.fejoa.repository

import org.fejoa.support.IOException
import org.fejoa.support.assert


object CommonAncestorsFinder {
    // Chain of consecutive commits. If a commit has multiple parent only one parent is followed.
    class SingleCommitChain {
        var commits: MutableList<Commit> = ArrayList()
        var reachedFirstCommit = false

        val oldest: Commit
            get() {
                assert(commits.size != 0)
                return commits[commits.size - 1]
            }

        constructor(head: Commit) {
            commits.add(head)
        }

        fun clone(): SingleCommitChain {
            val clone = SingleCommitChain()
            clone.commits.addAll(commits)
            clone.reachedFirstCommit = reachedFirstCommit
            return clone
        }

        private constructor()

        // make the chain terminate with the commonAncestor
        fun truncate(commonAncestor: Commit) {
            val index = commits.indexOf(commonAncestor)
            while (commits.size > index + 1)
                commits.removeAt(index + 1)
        }
    }

    class Chains {
        val chains: MutableList<SingleCommitChain> = ArrayList()

        val shortestChain: SingleCommitChain?
            get() {
                var length = Int.MAX_VALUE
                var shortestChain: SingleCommitChain? = null
                for (chain in chains) {
                    if (chain.commits.size < length) {
                        length = chain.commits.size
                        shortestChain = chain
                    }
                }
                return shortestChain
            }

        suspend fun loadCommits(accessor: CommitCache, numberOfCommits: Int) {
            for (chain in ArrayList(chains))
                CommonAncestorsFinder.loadCommits(accessor, chain, numberOfCommits, this)
        }

        fun allChainsFinished(): Boolean {
            for (chain in chains) {
                if (!chain.reachedFirstCommit)
                    return false
            }
            return true
        }
    }

    suspend private fun loadCommits(commitCache: CommitCache, commitChain: SingleCommitChain,
                            numberOfCommits: Int, result: Chains) {
        if (commitChain.reachedFirstCommit)
            return
        var oldest = commitChain.oldest
        for (i in 0 until numberOfCommits) {
            val parents = oldest.parents
            if (parents.size == 0) {
                commitChain.reachedFirstCommit = true
                return
            }
            for (p in 1 until parents.size) {
                val parent = parents.get(p)
                val clone = commitChain.clone()
                val nextCommit = commitCache.getCommit(parent)
                        ?: throw Exception("Can't find commit ${parent.value}")
                clone.commits.add(nextCommit)
                result.chains.add(clone)
                // follow this chain for a bit so that we stay on the same depth level
                loadCommits(commitCache, clone, numberOfCommits - i - 1, result)
            }

            oldest = commitCache.getCommit(parents[0])
                    ?: throw Exception("Can't find commit ${parents[0].value}")
            commitChain.commits.add(oldest)
        }
    }

    suspend private fun findCommonAncestorInOthers(localChain: SingleCommitChain, otherChain: SingleCommitChain): Commit? {
        //TODO: can be optimized by remembering which combinations we already checked, i.e. maintain a marker per chain
        for (other in otherChain.commits) {
            for (local in localChain.commits) {
                if (local.getHash() == other.getHash())
                    return other
            }
        }
        return null
    }

    /**
     * @return all commit chains that lead to common ancestors
     */
    suspend fun find(local: CommitCache, localCommit: Commit,
             others: CommitCache, othersCommit: Commit): Chains {
        assert(localCommit != null)
        assert(othersCommit != null)
        val loadCommitsNumber = 3

        val localChains = Chains()
        localChains.chains.add(SingleCommitChain(localCommit))
        val ongoingOthersChains = Chains()
        ongoingOthersChains.chains.add(SingleCommitChain(othersCommit))

        val results = Chains()
        while (ongoingOthersChains.chains.size > 0) {
            // check if all chains are finished
            if (localChains.allChainsFinished() && ongoingOthersChains.allChainsFinished())
                throw IOException("No common ancestors.")

            localChains.loadCommits(local, loadCommitsNumber)
            ongoingOthersChains.loadCommits(others, loadCommitsNumber)

            for (localChain in localChains.chains) {
                val iter = ongoingOthersChains.chains.iterator()
                while (iter.hasNext()) {
                    val otherChain = iter.next()
                    val commonAncestor = findCommonAncestorInOthers(localChain, otherChain)
                    if (commonAncestor != null) {
                        iter.remove()
                        otherChain.truncate(commonAncestor)
                        results.chains.add(otherChain)
                    }
                }
            }
        }

        return results
    }

    suspend fun collectAllChains(local: CommitCache, localCommit: Commit): Chains {
        val chains = Chains()
        val startCommitChain = SingleCommitChain(localCommit)
        chains.chains.add(startCommitChain)
        loadCommits(local, startCommitChain, Int.MAX_VALUE, chains)
        return chains
    }
}
