package org.fejoa.repository.sync

import org.fejoa.chunkcontainer.ChunkContainer
import org.fejoa.chunkcontainer.ChunkContainerNode
import org.fejoa.chunkcontainer.ChunkContainerRef
import org.fejoa.storage.Hash
import org.fejoa.network.RemotePipe
import org.fejoa.repository.*
import org.fejoa.storage.ChunkAccessor
import org.fejoa.storage.Config
import org.fejoa.storage.HashValue
import org.fejoa.support.*


class PushRequest(private val repository: Repository) {

    suspend private fun collectChangedContainers(transaction: ChunkAccessors.Transaction,
                                                 chains: CommonAncestorsFinder.Chains): Collection<ChunkContainerRef> {
        val list: MutableMap<HashValue, ChunkContainerRef> = HashMap()
        for (chain in chains.chains)
            collectChangedContainers(transaction, chain, list)
        return list.values
    }

    suspend private fun collectChangedContainers(transaction: ChunkAccessors.Transaction,
                                                 chain: CommonAncestorsFinder.SingleCommitChain,
                                                 map: MutableMap<HashValue, ChunkContainerRef>) {
        for (i in 0 until chain.commits.size - 1) {
            val parent = chain.commits[i + 1]
            val child = chain.commits[i]
            if (map.contains(child.getHash().value))
                continue

            collectChangedContainers(transaction, parent, child, map)
        }
    }

    suspend private fun collectChangedContainers(transaction: ChunkAccessors.Transaction, parent: Commit, child: Commit,
                                                 map: MutableMap<HashValue, ChunkContainerRef>) {
        // add child containers
        val childCommitRef = repository.objectIndex.getCommitStorageContainer(child.getHash())
                ?: throw Exception("Missing commit")
        map[childCommitRef.boxHash] = childCommitRef
        val childTreeRef = repository.objectIndex.getDirectoryStorageContainer(child.dir)
                ?: throw Exception("Missing directory")
        map[childTreeRef.boxHash] = childTreeRef


        // diff of the commit trees
        var parentDir = Directory.readRoot(parent.dir, repository.objectIndex)
        val nextDir = Directory.readRoot(child.dir, repository.objectIndex)

        val diffIterator = DirectoryDiffIterator("", parentDir, nextDir)
        while (diffIterator.hasNext()) {
            val change = diffIterator.next()
            // we are only interesting in modified and added changes
            if (change.type === DiffIterator.Type.REMOVED)
                continue

            if (change.theirs!!.isFile())
                collectFile(change.theirs.hash, change.path, map)
            else
                collectWholeDir(change.path, change.theirs as Directory, map)
        }
    }

    suspend private fun collectFile(hash: Hash, path: String, map: MutableMap<HashValue, ChunkContainerRef>) {
        val fileRef = repository.objectIndex.getBlobStorageContainer(path, hash)
            ?: throw Exception("Missing file")
        map[fileRef.boxHash] = fileRef
    }

    suspend private fun collectWholeDir(path: String, directory: Directory,
                                        map: MutableMap<HashValue, ChunkContainerRef>) {
        for (entry in directory.getChildren()) {
            val childPath = PathUtils.appendDir(path, entry.name)
            if (entry.isFile())
                collectFile(entry.hash, childPath, map)
            else {
                collectWholeDir(childPath, entry as Directory, map)
            }
        }
    }


    suspend private fun collectChunkContainer(pointer: ChunkContainerRef, chunks: MutableSet<HashValue>) {
        chunks.add(pointer.boxHash)
        // TODO: be more efficient and calculate the container diff
        val accessor = repository.objectIndex.getChunkAccessor(pointer)
        val chunkContainer = ChunkContainer.read(accessor, pointer)
        getChunkContainerNodeChildChunks(chunkContainer, accessor, chunks)
    }

    suspend private fun getChunkContainerNodeChildChunks(node: ChunkContainerNode, accessor: ChunkAccessor,
                                                         chunks: MutableSet<HashValue>) {
        for (chunkPointer in node.chunkPointers) {
            chunks.add(chunkPointer.boxHash)
            if (!ChunkContainerNode.isDataPointer(chunkPointer)) {
                val child = ChunkContainerNode.read(accessor, node, chunkPointer)
                getChunkContainerNodeChildChunks(child, accessor, chunks)
            }
        }
    }

    suspend fun push(remotePipe: RemotePipe, transaction: ChunkAccessors.Transaction, branch: String)
            : Request.ResultType {
        val rawTransaction = transaction.getRawAccessor()

        // WARNING race condition: We have to use the commit from the branch log because we send this log entry
        // straight to the server. This means the headCommit and the branch log entry must be in sync!
        val localLogTip = repository.log.getHead().await() ?: return Request.ResultType.ERROR

        val headCommitPointer = repository.branchLogIO.readFromLog(localLogTip.message)
        val headCommit = repository.commitCache.getCommit(headCommitPointer.head)
                ?: throw Exception("Failed to read commit")

        val containersToPush: MutableList<ChunkContainerRef>
        val remoteLogTip = LogEntryRequest.getRemoteTip(remotePipe, branch)
        if (remoteLogTip != null) { // remote has this branch, only push the diff to the common ancestor
            val remoteRepoRef = repository.branchLogIO.readFromLog(remoteLogTip.message)
            val remoteCommit = repository.commitCache.getCommit(remoteRepoRef.head)
                    ?: return Request.ResultType.PULL_REQUIRED

            val chainsToPush = CommonAncestorsFinder.find(repository.commitCache, remoteCommit,
                    repository.commitCache, headCommit)

            var remoteCommitIsCommonAncestor = false
            for (chain in chainsToPush.chains) {
                if (chain.oldest.getHash() == remoteCommit.getHash()) {
                    remoteCommitIsCommonAncestor = true
                    break
                }
            }
            if (!remoteCommitIsCommonAncestor)
                return Request.ResultType.PULL_REQUIRED

            containersToPush = collectChangedContainers(transaction, chainsToPush).toMutableList()
        } else {
            // push everything
            containersToPush = repository.objectIndex.listChunkContainers().map { it.second }.toMutableList()
        }

        // add object index
        containersToPush.add(repository.objectIndex.chunkContainer.ref)
        val chunksToPush: MutableSet<HashValue> = HashSet()
        containersToPush.forEach {
            collectChunkContainer(it, chunksToPush)
        }

        val remoteChunks = HasChunksRequest.hasChunks(remotePipe, chunksToPush.toList())
        for (chunk in remoteChunks)
            chunksToPush.remove(chunk)

        // start request

        val outStream = remotePipe.outStream
        Request.writeRequestHeader(outStream, Request.RequestType.PUT_CHUNKS)
        StreamHelper.writeString(outStream, branch)
        // expected remote rev
        val expectedRemoteId = remoteLogTip?.entryId ?: Config.newBoxHash()
        outStream.write(expectedRemoteId.bytes)
        // our message
        outStream.write(localLogTip.entryId.bytes)
        StreamHelper.writeString(outStream, localLogTip.message)
        outStream.writeInt(chunksToPush.size)

        for (chunk in chunksToPush) {
            outStream.write(chunk.bytes)
            val buffer = rawTransaction.getChunk(chunk).await()
            outStream.writeInt(buffer.size)
            outStream.write(buffer)
        }

        // read response
        val result = Request.receiveHeader(remotePipe.inStream, Request.RequestType.PUT_CHUNKS)
        if (result < 0)
            return Request.ResultType.ERROR
        return Request.ResultType.OK

    }
}
