package org.fejoa.repository

import org.fejoa.storage.HashValue
import org.fejoa.storage.ChunkAccessor
import org.fejoa.storage.ChunkTransaction
import org.fejoa.chunkcontainer.ChunkContainer
import org.fejoa.chunkcontainer.ChunkContainerNode
import org.fejoa.chunkcontainer.ChunkContainerRef
import org.fejoa.support.assert
import org.fejoa.support.await


abstract class Job(private val parent: Job?) {
    private val childJobs = ArrayList<Job>()

    abstract val requestedChunks: List<HashValue>

    init {
        parent?.childJobs?.add(this)
    }

    protected abstract suspend fun enqueueJobsAfterChunksFetched(chunkFetcher: ChunkFetcher)

    suspend fun onChunksFetched(chunkFetcher: ChunkFetcher) {
        enqueueJobsAfterChunksFetched(chunkFetcher)
        checkIfDone(chunkFetcher)
    }

    open suspend fun onDone(chunkFetcher: ChunkFetcher) {

    }

    private suspend fun onChildDone(job: Job, chunkFetcher: ChunkFetcher) {
        val removed = childJobs.remove(job)
        assert(removed)
        checkIfDone(chunkFetcher)
    }

    private suspend fun checkIfDone(chunkFetcher: ChunkFetcher) {
        if (childJobs.size == 0) {
            onDone(chunkFetcher)
            // have new jobs been added?
            if (parent != null && childJobs.size == 0)
                parent.onChildDone(this, chunkFetcher)
        }
    }
}

internal abstract class RootObjectJob(parent: Job?, protected val accessor: ChunkAccessor,
                                      protected val containerRef: ChunkContainerRef) : Job(parent) {

    override val requestedChunks: List<HashValue>
        get() = listOf(containerRef.boxHash)
}

internal class GetChunkContainerNodeJob(parent: Job?, private val accessor: ChunkAccessor, private val node: ChunkContainerNode) : Job(parent) {

    override val requestedChunks: List<HashValue>
        get() {
            val children = ArrayList<HashValue>()
            for (pointer in node.chunkPointers)
                children.add(pointer.boxHash)
            return children
        }

    override suspend fun enqueueJobsAfterChunksFetched(chunkFetcher: ChunkFetcher) {
        if (node.isDataLeafNode)
            return
        for (child in node.chunkPointers) {
            val childNode = ChunkContainerNode.read(accessor, node, child)
            chunkFetcher.enqueueJob(GetChunkContainerNodeJob(this, accessor, childNode))
        }
    }
}

internal open class GetChunkContainerJob(parent: Job?, accessor: ChunkAccessor, pointer: ChunkContainerRef)
    : RootObjectJob(parent, accessor, pointer) {
    protected var chunkContainer: ChunkContainer? = null

    override suspend fun enqueueJobsAfterChunksFetched(chunkFetcher: ChunkFetcher) {
        chunkContainer = ChunkContainer.read(accessor, containerRef)
        chunkFetcher.enqueueJob(GetChunkContainerNodeJob(this, accessor, chunkContainer!!))
    }
}

internal class GetObjectIndexJob(parent: Job?, val transaction: ChunkAccessors.Transaction,
                                 indexRef: ChunkContainerRef, val config: RepositoryConfig)
    : GetChunkContainerJob(parent, transaction.getCommitAccessor(indexRef.containerSpec), indexRef) {
    private var doneCount = 0
    var objectIndex: ObjectIndex? = null
        private set

    override suspend fun onDone(chunkFetcher: ChunkFetcher) {
        if (doneCount > 0)
            return
        doneCount++

        // we only read the chunk container so we don't need a config
        objectIndex = ObjectIndex.open(config, chunkContainer!!, transaction)
        chunkFetcher.enqueueObjectIndexJob(objectIndex!!)
    }
}


class ChunkFetcher(private val transaction: ChunkAccessors.Transaction, private val fetcherBackend: FetcherBackend) {
    private var ongoingJobs: MutableList<Job> = ArrayList()

    interface FetcherBackend {
        suspend fun fetch(transaction: ChunkTransaction, requestedChunks: List<HashValue>)
    }

    internal inner class ChunkRequest(jobs: List<Job>) {
        val requestedChunks = ArrayList<HashValue>()

        init {
            for (job in jobs)
                requestedChunks.addAll(job.requestedChunks)
        }
    }

    suspend fun enqueueRepositoryJob(repositoryRef: RepositoryRef) {
        enqueueJob(GetObjectIndexJob(null, transaction, repositoryRef.objectIndexRef, repositoryRef.config))
    }

    suspend fun enqueueObjectIndexJob(objectIndex: ObjectIndex) {
        val rawAccessor = transaction.getRawAccessor()

        objectIndex.listChunkContainers().map { it.second }
                .filterNot { rawAccessor.hasChunk(it.boxHash).await() }
                .forEach {
                    enqueueJob(GetChunkContainerJob(null, objectIndex.getChunkAccessor(it),
                            it))
                }
    }

    fun enqueueJob(job: Job) {
        ongoingJobs.add(job)
    }

    suspend fun fetch() {
        val rawAccessor = transaction.getRawAccessor()
        while (ongoingJobs.size > 0) {
            val currentJobs = ongoingJobs
            ongoingJobs = ArrayList()

            val chunkRequest = ChunkRequest(currentJobs)
            fetcherBackend.fetch(rawAccessor, chunkRequest.requestedChunks)
            for (job in currentJobs)
                job.onChunksFetched(this)
        }
        transaction.finishTransaction()
    }

    companion object {

        fun createLocalFetcher(targetTransaction: ChunkAccessors.Transaction,
                               source: ChunkTransaction): ChunkFetcher {
            return ChunkFetcher(targetTransaction, object : FetcherBackend {
                override suspend fun fetch(target: ChunkTransaction, requestedChunks: List<HashValue>) {
                    for (requestedChunk in requestedChunks) {
                        val buffer = source.getChunk(requestedChunk).await()
                        val result = target.putChunk(buffer).await()
                        if (result.key != requestedChunk)
                            throw Exception("Hash miss match.")
                    }
                }
            })
        }
    }
}
