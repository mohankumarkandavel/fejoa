package org.fejoa.repository

import org.fejoa.chunkcontainer.*
import org.fejoa.storage.*
import org.fejoa.support.IOException
import org.fejoa.support.readAll


class IODatabaseCC(root: Directory, val objectIndex: ObjectIndex,
                   private var transaction: ChunkAccessors.Transaction,
                   val config: ContainerSpec) : IODatabase {
    var treeAccessor = TreeAccessor(root, transaction)
    // Keeps track of potentially modified chunk containers.
    // maps path to ChunkContainer and the initial ChunkContainer hash
    private val editingChunkContainers = HashMap<String, Pair<ChunkContainer, Hash>>()
    private val openHandles = HashMap<String, MutableList<ChunkContainerRandomDataAccess>>()

    fun getModifiedChunkContainer(): Map<String, ChunkContainer> {
        return editingChunkContainers.filter {
            val pair = it.value
            pair.first.ref.hash != pair.second
        }.mapValues { it.value.first }
    }

    fun clearModifiedChunkContainer() {
        editingChunkContainers.clear()
        openHandles.forEach {
            val chunkContainer = it.value.firstOrNull {
                it.mode.has(RandomDataAccess.Mode.WRITE) && it.getChunkContainer() != null
            }?.getChunkContainer()

            if (chunkContainer != null) {
                editingChunkContainers[it.key] = chunkContainer to chunkContainer.ref.hash.clone()
            }
        }
    }

    fun setRootDirectory(root: Directory) {
        this.treeAccessor = TreeAccessor(root, transaction)
    }

    fun setTransaction(transaction: ChunkAccessors.Transaction) {
        this.transaction = transaction
        this.treeAccessor = TreeAccessor(treeAccessor.root, transaction)
    }

    private fun registerHandle(path: String, handle: ChunkContainerRandomDataAccess) {
        var list: MutableList<ChunkContainerRandomDataAccess>? = openHandles[path]
        if (list == null) {
            list = ArrayList()
            openHandles.put(path, list)
        }
        list.add(handle)
    }

    fun getRootDirectory(): Directory {
        return treeAccessor.root
    }

    private fun unregisterHandel(path: String, randomDataAccess: ChunkContainerRandomDataAccess) {
        val list = openHandles[path] ?: return
        list.remove(randomDataAccess)
        // TODO: use weak references again
        /*
        val it = list.iterator()
        while (it.hasNext()) {
            val weakReference = it.next()
            if (weakReference.get() === randomDataAccess) {
                it.remove()
                break
            }
        }*/
    }

    private fun getOpenHandles(path: String): List<ChunkContainerRandomDataAccess> {
        val list = openHandles.get(path) ?: return emptyList()

        val toRemove = ArrayList<ChunkContainerRandomDataAccess>()
        val refs = ArrayList<ChunkContainerRandomDataAccess>()
        for (entry in list) {
            val randomDataAccess = entry
            if (randomDataAccess == null) {
                toRemove.add(entry)
                continue
            }
            refs.add(randomDataAccess)
        }
        for (entry in toRemove)
            list.remove(entry)
        if (list.size == 0)
            openHandles.remove(path)
        return refs
    }

    private fun findOpenChunkContainer(path: String): ChunkContainer? {
        val refs = getOpenHandles(path)
        return if (refs.size == 0) null else refs[0].getChunkContainer()
    }

    private fun createIOCallback(path: String): ChunkContainerRandomDataAccess.IOCallback {
        return object : ChunkContainerRandomDataAccess.IOCallback() {
            private suspend fun flushOngoingWrites(veto: ChunkContainerRandomDataAccess) {
                val openHandles = getOpenHandles(path)
                for (randomDataAccess in openHandles) {
                    if (randomDataAccess === veto)
                        continue
                    if (!randomDataAccess.mode.has(RandomDataAccess.Mode.WRITE))
                        continue
                    randomDataAccess.flush()
                }
            }

            override suspend fun requestRead(caller: ChunkContainerRandomDataAccess) {
                flushOngoingWrites(caller)
            }

            override suspend fun requestWrite(caller: ChunkContainerRandomDataAccess) {
                flushOngoingWrites(caller)
            }

            override fun onClosed(caller: ChunkContainerRandomDataAccess) {
                val container = caller.getChunkContainer()!!
                if (caller.mode.has(RandomDataAccess.Mode.WRITE))
                    treeAccessor.putBlob(path, container.ref.hash)
                unregisterHandel(path, caller)
            }
        }
    }

    private fun createNewHandle(path: String, openFlags: RandomDataAccess.Mode): ChunkContainerRandomDataAccess {
        val chunkContainer = ChunkContainer.create(transaction.getFileAccessor(config, path), config)
        val randomDataAccess = ChunkContainerRandomDataAccess(chunkContainer,
                openFlags, createIOCallback(path), config.boxSpec.dataNormalization)
        registerHandle(path, randomDataAccess)

        if (openFlags.has(RandomDataAccess.Mode.WRITE))
            editingChunkContainers[path] = chunkContainer to chunkContainer.ref.hash.clone()

        return randomDataAccess
    }

    suspend override fun probe(path: String): IODatabase.FileType {
        val entry = treeAccessor.get(path) ?: return IODatabase.FileType.NOT_EXISTING
        return when (entry.entryType) {
            DirectoryEntry.EntryType.LAST_DIR_ENTRY,
            DirectoryEntry.EntryType.EMPTY_DIR -> throw Exception("Unexpected entry type")
            DirectoryEntry.EntryType.DIR -> IODatabase.FileType.DIRECTORY
            DirectoryEntry.EntryType.BLOB -> IODatabase.FileType.FILE
            DirectoryEntry.EntryType.SYMBOLIC_LINK -> TODO()
            DirectoryEntry.EntryType.HARD_LINK -> TODO()
        }
    }

    /**
     * @return a BlobEntry with a loaded chunk container
     */
    suspend private fun loadChunkContainer(path: String, mode: RandomDataAccess.Mode): ChunkContainer {
        val entry = treeAccessor.get(path) ?: throw IOException("Entry not found: " + path)
        if (!entry.isFile())
            throw IOException("Path is not a file")

        editingChunkContainers[path]?.let { return it.first }

        val blobEntry = entry as BlobEntry
        val chunkContainer = objectIndex.getBlob(path, blobEntry.hash)
                ?: throw IOException("Can't load object from object index: $path")

        if (mode.has(RandomDataAccess.Mode.WRITE))
            editingChunkContainers[path] = chunkContainer to chunkContainer.ref.hash.clone()
        return chunkContainer
    }

    suspend override fun getHash(path: String): HashValue {
        synchronized(this) {
            var chunkContainer = findOpenChunkContainer(path)
            if (chunkContainer != null)
                return chunkContainer.hash()

            val entry = treeAccessor[path] ?: throw IOException("File not fount: $path")
            if (!entry.isFile())
                throw IOException("Path is not a file: $path")

            return entry.hash.value
        }
    }

    suspend fun flush(): Hash {
        getModifiedChunkContainer().forEach {
            treeAccessor.putBlob(it.key, it.value.ref.hash)
        }

        val paths = ArrayList(openHandles.keys)
        for (path in paths) {
            for (randomDataAccess in getOpenHandles(path)) {
                if (!randomDataAccess.mode.has(RandomDataAccess.Mode.WRITE))
                    continue
                randomDataAccess.flush()
                treeAccessor.putBlob(path, randomDataAccess.getChunkContainer()!!.ref.hash)
            }
        }
        return treeAccessor.build()
    }

    suspend override fun open(path: String, mode: RandomDataAccess.Mode): RandomDataAccess = synchronized(this) {
        var randomDataAccess: ChunkContainerRandomDataAccess
        // first try to find an open chunk container
        var chunkContainer = findOpenChunkContainer(path)
        if (chunkContainer != null) {
            randomDataAccess = ChunkContainerRandomDataAccess(chunkContainer,
                    mode, createIOCallback(path), config.boxSpec.dataNormalization)
            registerHandle(path, randomDataAccess)
        } else {
            // try to open a chunk container
            try {
                chunkContainer = loadChunkContainer(path, mode)
                randomDataAccess = ChunkContainerRandomDataAccess(chunkContainer,
                        mode, createIOCallback(path), config.boxSpec.dataNormalization)
                registerHandle(path, randomDataAccess)
            } catch (e: IOException) {
                if (!mode.has(RandomDataAccess.Mode.WRITE))
                    throw e
                randomDataAccess = createNewHandle(path, mode)
            }

        }
        if (mode.has(RandomDataAccess.Mode.TRUNCATE))
            randomDataAccess.truncate(0)
        else if (mode.has(RandomDataAccess.Mode.APPEND))
            randomDataAccess.seek(randomDataAccess.length())
        return randomDataAccess
    }

    suspend override fun remove(path: String) {
        treeAccessor.remove(path)
    }

    suspend override fun listFiles(path: String): Collection<String> {
        val directory = treeAccessor.get(path) ?: return emptyList()
        if (directory.isFile())
            return emptyList()
        return (directory as Directory).getChildren().filter { it.isFile() }.map { it.name }
    }

    suspend override fun listDirectories(path: String): Collection<String> {
        val directory = treeAccessor.get(path) ?: return emptyList()
        if (directory.isFile())
            return emptyList()
        return (directory as Directory).getChildren().filter { !it.isFile() }.map { it.name }
    }

    suspend override fun readBytes(path: String): ByteArray {
        val randomDataAccess = open(path, RandomDataAccess.Mode.READ)
        val date = randomDataAccess.readAll()
        randomDataAccess.close()
        return date
    }

    suspend override fun putBytes(path: String, data: ByteArray) {
        val randomDataAccess = open(path, RandomDataAccess.Mode.TRUNCATE) as ChunkContainerRandomDataAccess
        randomDataAccess.write(data)
        randomDataAccess.close()
    }
}
