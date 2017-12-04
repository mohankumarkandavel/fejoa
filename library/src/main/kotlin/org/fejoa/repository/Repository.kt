package org.fejoa.repository

import org.fejoa.chunkcontainer.*
import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.SecretKey
import org.fejoa.storage.*
import org.fejoa.support.*


class CryptoConfig(val secretKey: SecretKey, val symmetric: CryptoSettings.Symmetric)

// TODO remove crypto config?
class RepositoryConfig(val hashSpec: HashSpec,
                       val boxSpec: BoxSpec = BoxSpec(),
                       val revLog: RevLog = RevLog(),
                       val crypto: CryptoConfig? = null) {

    class RevLog(val maxRevEntrySize: Long = 4 * 1024 * 1024)

    val containerSpec: ContainerSpec
        get() = ContainerSpec(hashSpec.createChild(), boxSpec)
}


// TODO: include revlog settings?
class RepositoryRef(val objectIndexRef: ChunkContainerRef, val head: Hash) {
    companion object {
        suspend fun read(inStream: AsyncInStream): RepositoryRef {
            val objectIndexRef = ChunkContainerRef.read(inStream, null)
            val head = Hash.read(inStream, objectIndexRef.hash.spec)
            return RepositoryRef(objectIndexRef, head)
        }
    }

    suspend fun write(outStream: AsyncOutStream) {
        objectIndexRef.write(outStream)
        head.write(outStream)
    }
}


class Repository private constructor(private val branch: String,
                                     val branchBackend: StorageBackend.BranchBackend,
                                     private val accessors: ChunkAccessors,
                                     transaction: ChunkAccessors.Transaction,
                                     val log: BranchLog,
                                     val objectIndex: ObjectIndex,
                                     val config: RepositoryConfig): Database {
    private var transaction: LogRepoTransaction = LogRepoTransaction(transaction)
    private var headCommit: Commit? = null
    val commitCache = CommitCache(this)
    val ioDatabase = IODatabaseCC(Directory("", config.hashSpec.createChild()), objectIndex, transaction, config.containerSpec)
    private val mergeParents = ArrayList<Hash>()
    val branchLogIO: BranchLogIO

    init {
        if (config.crypto != null)
            branchLogIO = RepositoryBuilder.getEncryptedBranchLogIO(config.crypto.secretKey, config.crypto.symmetric)
        else
            branchLogIO = RepositoryBuilder.getPlainBranchLogIO()
    }

    companion object {
        fun create(branch: String, branchBackend: StorageBackend.BranchBackend, config: RepositoryConfig): Repository {
            val containerSpec = config.containerSpec
            val accessors: ChunkAccessors = RepoChunkAccessors(branchBackend.getChunkStorage(), config)
            val log: BranchLog = branchBackend.getBranchLog()
            val transaction = accessors.startTransaction()
            val objectIndexCC = ChunkContainer.create(transaction.getObjectIndexAccessor(containerSpec),
                    containerSpec)
            val objectIndex = ObjectIndex.create(config, objectIndexCC)
            return Repository(branch, branchBackend, accessors, transaction, log, objectIndex, config)
        }

        suspend fun open(branch: String, ref: RepositoryRef, branchBackend: StorageBackend.BranchBackend,
                         crypto: CryptoConfig?): Repository {
            val repoConfig = RepositoryConfig(ref.objectIndexRef.hash.spec, ref.objectIndexRef.boxSpec, crypto = crypto)

            val containerSpec = repoConfig.containerSpec
            val accessors: ChunkAccessors = RepoChunkAccessors(branchBackend.getChunkStorage(), repoConfig)
            val log: BranchLog = branchBackend.getBranchLog()
            val transaction = accessors.startTransaction()
            val objectIndexCC = ChunkContainer.read(transaction.getObjectIndexAccessor(containerSpec),
                    ref.objectIndexRef)
            val objectIndex = ObjectIndex.open(repoConfig, objectIndexCC)

            val repository = Repository(branch, branchBackend, accessors, transaction, log, objectIndex, repoConfig)
            repository.setHeadCommit(ref.head)
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
        val rootDir = Directory.readRoot(headCommit!!.dir, objectIndex)
        ioDatabase.setRootDirectory(rootDir)
    }

    override suspend fun getHead(): Hash {
        return getHeadCommit()?.getHash()?.clone() ?: Hash.createChild(config.hashSpec.createChild())
    }

    private fun getParents(): Collection<HashValue> {
        return headCommit?.parents?.map { it.value } ?: emptyList()
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
        this.mergeParents.addAll(mergeParents.map { it.getHead() })
        return result
    }

    suspend fun isModified(): Boolean {
        if (headCommit == null)
            return ioDatabase.treeAccessor.root.getChildren().isNotEmpty()

        val headHash = headCommit!!.dir
        val dirHash = ioDatabase.flush()
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

            if (!oursIsModified && getHead() == theirs.getHead())
                return Database.MergeResult.FAST_FORWARD
        }

        // pull missing objects into the our repository
        copyObjectIndexChunks(theirs.transaction.getRawAccessor(), theirs.objectIndex)
        val commit = theirs.getHeadCommit() ?: throw Exception("Invalid branch")
        copyMissingObjectRefs(commit, theirs.objectIndex, theirs.commitCache)

        if (allowFastForward && !oursIsModified && !theirsIsModified) {
            if (headCommit == null) {
                // we are empty; just use the other branch's head
                setHeadCommit(theirs.getHead())
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
                setHeadCommit(theirs.getHead())
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
        val theirsRoot = Directory.readRoot(theirsCommit.dir, theirsObjectIndex)
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
            val parent = theirsCommitCache.getCommit(parentRef) ?: throw Exception("Can't load commit")
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
            return headCommit?.getHash() ?: Hash.createChild(config.hashSpec.createChild())

        // flush in any case to write open write handles and to be able to determine if we need to commit
        val rootTree = ioDatabase.flush()
        if (mergeParents.isEmpty() && headCommit != null && headCommit!!.getHash() == rootTree)
            return headCommit!!.getHash()

        // write entries to the objectIndex
        ioDatabase.getModifiedChunkContainer().forEach {
            objectIndex.putBlob(it.key, it.value)
        }
        ioDatabase.clearModifiedChunkContainer()
        writeTree(ioDatabase.getRootDirectory())
        // write the rootTree to the object index
        val commit = Commit(rootTree, Hash.createChild(config.hashSpec))
        if (headCommit != null)
            commit.parents.add(headCommit!!.getHash())
        for (mergeParent in mergeParents)
            commit.parents.add(mergeParent)
        if (commitSignature != null)
            commit.message = commitSignature.signMessage(message, rootTree.value, getParents())
        else
            commit.message = message
        writeCommit(commit)
        headCommit = commit

        val objectIndexRef =  objectIndex.flush()
        val repoRef = RepositoryRef(objectIndexRef, commit.getHash())
        transaction.finishTransaction()
        log.add(branchLogIO.logHash(repoRef), branchLogIO.writeToLog(repoRef), transaction.getObjectsWritten())
        transaction = LogRepoTransaction(accessors.startTransaction())
        ioDatabase.setTransaction(transaction)

        return commit.getHash()
    }

    suspend fun getRepositoryRef(): RepositoryRef {
        return RepositoryRef(objectIndex.chunkContainer.ref, getHead())
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