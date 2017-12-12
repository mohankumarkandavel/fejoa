package org.fejoa.repository

import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.fejoa.chunkcontainer.*
import org.fejoa.crypto.SymBaseCredentials
import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.storage.*
import org.fejoa.support.*


class RepositoryConfig(val hashSpec: HashSpec,
                       val boxSpec: BoxSpec = BoxSpec(),
                       val revLog: RevLogConfig = RevLogConfig()) {
    val containerSpec: ContainerSpec
        get() = ContainerSpec(hashSpec.createChild(), boxSpec)

    companion object {
        val HASH_SPEC_TAG = 0
        val BOX_SPEC_TAG = 1
        val REV_LOG_TAG = 2

        /**
         * @param default if hashSpec or boxSpec are not included in the buffer these values are taken from default
         */
        suspend fun read(protoBuffer: ProtocolBufferLight, default: ChunkContainerRef): RepositoryConfig {
            val hashSpec = protoBuffer.getBytes(HASH_SPEC_TAG)?.let {
                return@let HashSpec.read(ByteArrayInStream(it).toAsyncInputStream(), null)
            } ?: default.hash.spec

            val boxSpec = protoBuffer.getBytes(BOX_SPEC_TAG)?.let {
                return@let BoxSpec.read(ByteArrayInStream(it).toAsyncInputStream())
            } ?: default.boxSpec

            val revLog = protoBuffer.getBytes(REV_LOG_TAG)?.let {
                return@let ProtoBuf.load<RevLogConfig>(it)
            } ?: throw Exception("Rev log config expected")

            return RepositoryConfig(hashSpec, boxSpec, revLog)
        }
    }

    /**
     * @param default hashSpec and boxSpec are only written if they are different from the parameters in default
     */
    suspend fun write(protoBuffer: ProtocolBufferLight, default: ChunkContainerRef) {
        var outStream = AsyncByteArrayOutStream()
        if (default.hash.spec != hashSpec) {
            hashSpec.write(outStream)
            protoBuffer.put(HASH_SPEC_TAG, outStream.toByteArray())
        }
        if (default.boxSpec != boxSpec) {
            outStream = AsyncByteArrayOutStream()
            boxSpec.write(outStream)
            protoBuffer.put(BOX_SPEC_TAG, outStream.toByteArray())
        }

        protoBuffer.put(REV_LOG_TAG, ProtoBuf.dump(revLog))
    }
}


@Serializable
class RevLogConfig(@SerialId(0) val maxEntrySize: Long = 4 * 1024 * 1024)

class RepositoryRef(val objectIndexRef: ChunkContainerRef, val head: Hash, val config: RepositoryConfig) {
    companion object {
        val OBJECT_INDEX_REF_TAG = 0
        val HEAD_TAG = 1
        val CONFIG_TAG = 2

        suspend fun read(protoBuffer: ProtocolBufferLight): RepositoryRef {
            val objectIndexRef = protoBuffer.getBytes(OBJECT_INDEX_REF_TAG)?.let {
                return@let ChunkContainerRef.read(ByteArrayInStream(it).toAsyncInputStream(), null)
            } ?: throw Exception("Missing object index ref")

            val head = protoBuffer.getBytes(HEAD_TAG)?.let {
                return@let Hash.read(ByteArrayInStream(it).toAsyncInputStream(), null)
            } ?: throw Exception("Missing repo head")

            val config = protoBuffer.getBytes(CONFIG_TAG)?.let {
                val configBuffer = ProtocolBufferLight(it)
                return@let RepositoryConfig.read(configBuffer, objectIndexRef)
            } ?: throw Exception("Missing repo config")

            return RepositoryRef(objectIndexRef, head, config)
        }
    }

    suspend fun write(protoBuffer: ProtocolBufferLight) {
        var outStream = AsyncByteArrayOutStream()
        objectIndexRef.write(outStream)
        protoBuffer.put(OBJECT_INDEX_REF_TAG, outStream.toByteArray())

        outStream = AsyncByteArrayOutStream()
        head.write(outStream)
        protoBuffer.put(HEAD_TAG, outStream.toByteArray())

        val configBuffer = ProtocolBufferLight()
        config.write(configBuffer, objectIndexRef)
        protoBuffer.put(CONFIG_TAG, configBuffer.toByteArray())
    }


}


class Repository private constructor(private val branch: String,
                                     val branchBackend: StorageBackend.BranchBackend,
                                     private val accessors: ChunkAccessors,
                                     transaction: ChunkAccessors.Transaction,
                                     val log: BranchLog,
                                     val objectIndex: ObjectIndex,
                                     val config: RepositoryConfig,
                                     val crypto: SymBaseCredentials?): Database {
    private var transaction: LogRepoTransaction = LogRepoTransaction(transaction)
    private var headCommit: Commit? = null
    val commitCache = CommitCache(this)
    val ioDatabase = IODatabaseCC(Directory("", config.hashSpec.createChild()), objectIndex, transaction, config.containerSpec)
    private val mergeParents = ArrayList<Hash>()
    val branchLogIO: BranchLogIO

    init {
        if (crypto != null)
            branchLogIO = RepositoryBuilder.getEncryptedBranchLogIO(crypto.secretKey, crypto.symmetric)
        else
            branchLogIO = RepositoryBuilder.getPlainBranchLogIO()
    }

    companion object {
        fun create(branch: String, branchBackend: StorageBackend.BranchBackend, config: RepositoryConfig,
                   crypto: SymBaseCredentials?): Repository {
            val containerSpec = config.containerSpec
            val accessors: ChunkAccessors = RepoChunkAccessors(branchBackend.getChunkStorage(), config, crypto)
            val log: BranchLog = branchBackend.getBranchLog()
            val transaction = accessors.startTransaction()
            val objectIndexCC = ChunkContainer.create(transaction.getObjectIndexAccessor(containerSpec),
                    containerSpec)
            val objectIndex = ObjectIndex.create(config, objectIndexCC)
            return Repository(branch, branchBackend, accessors, transaction, log, objectIndex, config, crypto)
        }

        suspend fun open(branch: String, ref: RepositoryRef, branchBackend: StorageBackend.BranchBackend,
                         crypto: SymBaseCredentials?): Repository {
            val repoConfig = RepositoryConfig(ref.objectIndexRef.hash.spec, ref.objectIndexRef.boxSpec)

            val containerSpec = repoConfig.containerSpec
            val accessors: ChunkAccessors = RepoChunkAccessors(branchBackend.getChunkStorage(), repoConfig, crypto)
            val log: BranchLog = branchBackend.getBranchLog()
            val transaction = accessors.startTransaction()
            val objectIndexCC = ChunkContainer.read(transaction.getObjectIndexAccessor(containerSpec),
                    ref.objectIndexRef)
            val objectIndex = ObjectIndex.open(repoConfig, objectIndexCC)

            val repository = Repository(branch, branchBackend, accessors, transaction, log, objectIndex, repoConfig,
                    crypto)
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

    suspend override fun merge(mergeParents: Collection<Database>, mergeStrategy: MergeStrategy)
            : Database.MergeResult {
        var result = Database.MergeResult.FAST_FORWARD
        val allowFastForward = mergeParents.size == 1
        mergeParents.forEach {
            val singleResult = mergeSingleBranch(it, mergeStrategy, allowFastForward)
            if (singleResult == Database.MergeResult.MERGED)
                result = singleResult
        }
        if (result == Database.MergeResult.FAST_FORWARD) {
            val repoRef = (mergeParents.first() as Repository).getRepositoryRef()
            log.add(branchLogIO.logHash(repoRef),
                    branchLogIO.writeToLogString(repoRef), transaction.getObjectsWritten())
        } else
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

    suspend private fun mergeSingleBranch(theirs: Database, mergeStrategy: MergeStrategy,
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
        val repoRef = RepositoryRef(objectIndexRef, commit.getHash(), config)
        transaction.finishTransaction()
        log.add(branchLogIO.logHash(repoRef), branchLogIO.writeToLogString(repoRef), transaction.getObjectsWritten())
        transaction = LogRepoTransaction(accessors.startTransaction())
        ioDatabase.setTransaction(transaction)

        return commit.getHash()
    }

    suspend fun getRepositoryRef(): RepositoryRef {
        return RepositoryRef(objectIndex.chunkContainer.ref, getHead(), config)
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