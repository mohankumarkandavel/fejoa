package org.fejoa.repository

import org.fejoa.chunkcontainer.*
import org.fejoa.crypto.CryptoInterface
import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.SecretKey
import org.fejoa.storage.*
import org.fejoa.support.IOException
import org.fejoa.support.await
import org.fejoa.support.getAll


class CryptoConfig(val crypto: CryptoInterface, val secretKey: SecretKey, val symmetric: CryptoSettings.Symmetric)

/**
 * @param ioFilters indicates which operations should be applied for serialization. The order and number of the list items
 * matters, e.g. listOf(IOFilter.COMPRESSED, IOFilter.ENCRYPTED) means that when writing a chunk, the chunk is first
 * compressed and then encrypted.
 */
class RepositoryConfig(val crypto: CryptoConfig? = null,
                       val hashSpec: HashSpec = HashSpec(),
                       val boxSpec: BoxSpec = BoxSpec(),
                       val revLog: RevLog = RevLog()) {

    class RevLog(val maxRevEntrySize: Long = 4 * 1024 * 1024)

    val containerSpec: ContainerSpec
        get() = ContainerSpec(hashSpec, boxSpec)
}


class Repository private constructor(private val branch: String, val objectIndex: ObjectIndex, val log: BranchLog,
                                     val accessors: ChunkAccessors,
                                     val commitCallback: CommitCallback,
                                     val config: RepositoryConfig): Database {
    private var headCommit: Commit? = null
    private var transaction: LogRepoTransaction = LogRepoTransaction(accessors.startTransaction())
    val commitCache = CommitCache(this)
    val ioDatabase: IODatabaseCC = IODatabaseCC(Directory(""), objectIndex, transaction,
            ContainerSpec(config.hashSpec, config.boxSpec))
    private val mergeParents = ArrayList<Hash>()

    companion object {
        fun create(branch: String, objectIndex: ObjectIndex, log: BranchLog, accessors: ChunkAccessors,
                   commitCallback: CommitCallback, config: RepositoryConfig): Repository {
            return Repository(branch, objectIndex, log, accessors, commitCallback, config)
        }

        suspend fun open(branch: String, log: BranchLog, accessors: ChunkAccessors,
                         commitCallback: CommitCallback, config: RepositoryConfig = RepositoryConfig()): Repository {

            val objectIndexRef = log.getHead().await()?.let {
                commitCallback.objectIndexRefFromLog(it.message)
            } ?: throw IOException("Can't read object index head")

            val transaction = accessors.startTransaction()
            val accessor = transaction.getCommitAccessor(objectIndexRef.containerSpec)
            val objectIndex = ObjectIndex.open(config,
                    ChunkContainer.read(accessor, objectIndexRef))

            val commitHash = objectIndex.getCommitEntries().firstOrNull()
                    ?: throw Exception("No commits in object index")
            val repository = Repository(branch, objectIndex, log, accessors, commitCallback, config)
            if (commitHash != null && !commitHash.value.isZero)
                repository.setHeadCommit(commitHash)

            return repository
        }
    }

    fun getCurrentTransaction(): ChunkAccessors.Transaction {
        return transaction
    }

    override fun getBranch(): String {
        return branch
    }

    fun getHeadCommit(): Commit? {
        return headCommit
    }

    suspend fun setHeadCommit(commit: Hash) {
        if (headCommit == null) {
            headCommit = Commit.read(commit, objectIndex)
        } else {
            headCommit = commitCache.getCommit(commit)
            if (headCommit == null)
                throw Exception("Invalid commit hash: ${commit.value}")
        }
        val rootDir = Directory.readRoot(headCommit!!.dir.hash, objectIndex)
        ioDatabase.setRootDirectory(rootDir)
    }

    override suspend fun getTip(): Hash {
        return getHeadCommit()?.getRef()?.hash?.clone() ?: Hash()
    }

    private fun getParents(): Collection<HashValue> {
        return headCommit?.parents?.map { it.hash.value } ?: emptyList()
    }

    suspend override fun merge(mergeParents: Collection<Database>, mergeStrategy: IMergeStrategy)
            : Database.MergeResult {
        var result = Database.MergeResult.FAST_FORWARD
        val allowFastForward = mergeParents.size == 1
        mergeParents.forEach {
            val singleResult = mergeSingleBranch(it, mergeStrategy, allowFastForward)
            if (singleResult == Database.MergeResult.MERGED)
                result = singleResult
        }
        this.mergeParents.addAll(mergeParents.map { it.getTip() })
        return result
    }

    suspend fun isModified(): Boolean {
        if (headCommit == null)
            return ioDatabase.treeAccessor.root.getChildren().isNotEmpty()

        val headHash = headCommit!!.dir.hash
        val dirHash = ioDatabase.flush().hash
        if (headHash != dirHash)
            return true
        return false
    }

    suspend private fun mergeSingleBranch(theirs: Database, mergeStrategy: IMergeStrategy,
                                          allowFastForward: Boolean): Database.MergeResult {
        if (theirs !is Repository)
            throw Exception("Unsupported repository")

        val oursIsModified = isModified()
        val theirsIsModified = theirs.isModified()
        if (allowFastForward && !theirsIsModified) {
            if (theirs.headCommit == null)
                return Database.MergeResult.FAST_FORWARD

            if (!oursIsModified && getTip() == theirs.getTip())
                return Database.MergeResult.FAST_FORWARD
        }

        // pull missing objects into the our repository
        copyObjectIndexChunks(theirs.transaction.getRawAccessor(), theirs.objectIndex)
        val commit = theirs.getHeadCommit() ?: throw Exception("Invalid branch")
        copyMissingObjectRefs(commit, theirs.objectIndex, theirs.commitCache)

        if (allowFastForward && !oursIsModified && !theirsIsModified) {
            if (headCommit == null) {
                // we are empty; just use the other branch's head
                setHeadCommit(theirs.getTip())
                return Database.MergeResult.FAST_FORWARD
            }

            // 1) Find common ancestor
            // 2) Merge head with otherBranch
            val chains = CommonAncestorsFinder.find(commitCache, headCommit!!,
                    theirs.commitCache, theirs.getHeadCommit()!!)

            val shortestChain = chains.shortestChain
                    ?: throw IOException("Branches don't have common ancestor.")
            if (shortestChain.oldest.getHash() == headCommit!!.getHash()) {
                // no local commits: just use the remote head
                setHeadCommit(theirs.getTip())
                return Database.MergeResult.FAST_FORWARD
            }
        }

        // merge branches
        mergeStrategy.merge(this, theirs)
        return Database.MergeResult.MERGED
    }

    /**
     * Adds the missing object refs to our object index
     */
    suspend private fun copyMissingObjectRefs(theirsCommit: Commit, theirsObjectIndex: ObjectIndex,
                                              theirsCommitCache: CommitCache) {
        // add commit
        writeCommit(theirsCommit)
        // add directory
        val theirsRoot = Directory.readRoot(theirsCommit.dir.hash, theirsObjectIndex)
        writeTree(theirsRoot)
        // add blobs
        val diff = ioDatabase.getRootDirectory().getDiff(theirsRoot,
                includeAllAdded = true, includeAllRemoved = false).getAll()
        diff.filter { (it.type == DiffIterator.Type.ADDED || it.type == DiffIterator.Type.MODIFIED)
                && it.theirs!!.isFile()}
                .forEach {
                    val theirs = it.theirs!!
                    val container = theirsObjectIndex.getBlob(it.path, theirs.hash)
                            ?: throw Exception("Blob not found ${it.path}")
                    objectIndex.putBlob(it.path, container)
                }
        for (parentRef in theirsCommit.parents) {
            val parent = theirsCommitCache.getCommit(parentRef.hash) ?: throw Exception("Can't load commit")
            copyMissingObjectRefs(parent, theirsObjectIndex, commitCache)
        }
    }

    /**
     * Copies all missing chunks, as listed in the object index, from the source to chunk store to our store.
     */
    suspend private fun copyObjectIndexChunks(source: ChunkTransaction,
                                              theirsObjectIndex: ObjectIndex) {
        val chunkFetcher = ChunkFetcher.createLocalFetcher(transaction, source)
        chunkFetcher.enqueueObjectIndexJob(theirsObjectIndex)
        chunkFetcher.fetch()
    }

    suspend override fun commit(message: ByteArray, signature: CommitSignature?): Hash = synchronized(this) {
        val hash = commitInternal(message, signature, mergeParents = mergeParents)
        mergeParents.clear()
        return hash
    }

    suspend private fun commitInternal(message: ByteArray, commitSignature: CommitSignature? = null,
                                       mergeParents: Collection<Hash>): Hash = synchronized(this) {
        // check if we need to commit
        if (!isModified() && mergeParents.isEmpty())
            return headCommit?.getHash() ?: Hash()

        // flush in any case to write open write handles and to be able to determine if we need to commit
        val rootTree = ioDatabase.flush()
        if (mergeParents.isEmpty() && headCommit != null && headCommit!!.dir.hash == rootTree.hash)
            return Hash()

        // write entries to the objectIndex
        ioDatabase.getModifiedChunkContainer().forEach {
            objectIndex.putBlob(it.key, it.value)
        }
        ioDatabase.clearModifiedChunkContainer()
        writeTree(ioDatabase.getRootDirectory())
        // write the rootTree to the object index
        val commit = Commit(rootTree)
        if (headCommit != null)
            commit.parents.add(headCommit!!.getRef())
        for (mergeParent in mergeParents)
            commit.parents.add(CommitRef(mergeParent))
        if (commitSignature != null)
            commit.message = commitSignature.signMessage(message, rootTree.hash.value, getParents())
        else
            commit.message = message
        writeCommit(commit)
        headCommit = commit

        val objectIndexHead =  objectIndex.flush()

        transaction.finishTransaction()
        log.add(commitCallback.logHash(objectIndexHead), commitCallback.objectIndexRefToLog(objectIndexHead),
                transaction.getObjectsWritten())
        transaction = LogRepoTransaction(accessors.startTransaction())
        ioDatabase.setTransaction(transaction)

        return commit.getRef().hash
    }

    suspend private fun writeTree(tree: Directory): Hash {
        val containerSpec = config.containerSpec
        val chunkContainer = ChunkContainer.create(transaction.getTreeAccessor(containerSpec),
                containerSpec)
        val outStream = ChunkContainerOutStream(chunkContainer)
        tree.writeRoot(outStream)
        outStream.close()
        objectIndex.putDir(chunkContainer)
        return chunkContainer.ref.hash
    }

    suspend private fun writeCommit(commit: Commit) {
        val containerSpec = config.containerSpec
        val chunkContainer = ChunkContainer.create(transaction.getCommitAccessor(containerSpec),
                containerSpec)
        val outStream = ChunkContainerOutStream(chunkContainer)
        commit.write(outStream)
        outStream.close()
        objectIndex.putCommit(chunkContainer)
    }

    suspend override fun getDiff(baseCommit: HashValue, endCommit: HashValue): DatabaseDiff {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    suspend override fun getHash(path: String): HashValue {
        return ioDatabase.getHash(path)
    }

    suspend override fun probe(path: String): IODatabase.FileType {
        return ioDatabase.probe(path)
    }

    suspend override fun open(path: String, mode: RandomDataAccess.Mode): RandomDataAccess {
        return ioDatabase.open(path, mode)
    }

    suspend override fun remove(path: String) {
        ioDatabase.remove(path)
    }

    suspend override fun listFiles(path: String): Collection<String> {
        return ioDatabase.listFiles(path)
    }

    suspend override fun listDirectories(path: String): Collection<String> {
        return ioDatabase.listDirectories(path)
    }

    suspend override fun readBytes(path: String): ByteArray {
        return ioDatabase.readBytes(path)
    }

    suspend override fun putBytes(path: String, data: ByteArray) {
        ioDatabase.putBytes(path, data)
    }
}