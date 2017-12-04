package org.fejoa.repository

import org.fejoa.chunkcontainer.*
import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.revlog.Revlog
import org.fejoa.storage.*
import org.fejoa.support.*


// [n Entries]
// |EntryRefs|
class EntryRefList {
    val entries: MutableList<EntryRef> = ArrayList()

    companion object {
        suspend fun read(inStream: AsyncInStream, parent: HashSpec?): EntryRefList {
            val nEntries = VarInt.read(inStream).first
            val entryRefList = EntryRefList()
            (0 until nEntries).forEach {
                entryRefList.entries.add(EntryRef.read(inStream, parent))
            }
            return entryRefList
        }
    }

    fun printToString(): String {
        var out = ""
        entries.forEach {
            if (out != "")
                out += "\n"
            out += "\t${it.type.name}\n"
            it.getHashes().forEach {
                out += "\t\t${it.value}"
            }
        }
        return out
    }

    suspend fun write(outStream: AsyncOutStream): Int {
        var bytesWritten = VarInt.write(outStream, entries.size)
        entries.forEach {
            bytesWritten += it.write(outStream)
        }
        return bytesWritten
    }

    fun push(entry: EntryRef) {
        entries.add(0, entry)
    }

    fun replace(old: EntryRef, new: EntryRef) {
        val index = entries.indexOf(old)
        if (index < 0) throw Exception("Old entry not found")
        entries.removeAt(index)
        entries.add(index, new)
    }

    fun getRefFor(hash: Hash): EntryRef? {
        return entries.firstOrNull { it.has(hash) }
    }
}

open class EntryRef(val type: RefType, val containerRef: ChunkContainerRef) {
    enum class RefType(val value: Int) {
        CHUNK_CONTAINER(0),
        CC_REV_LOG(1),
    }

    open fun has(hash: Hash): Boolean {
        return this.containerRef.hash.value == hash.value
    }

    open fun getHashes(): List<Hash> {
        return listOf(containerRef.hash)
    }

    // |RefType|
    // |ChunkContainerRef|
    companion object {
        suspend fun read(inStream: AsyncInStream, parent: HashSpec?): EntryRef {
            val refTypeValue = inStream.read()
            val refType = EntryRef.RefType.values().firstOrNull { it.value == refTypeValue }
                    ?: throw IOException("Unknown type")
            val containerRef = ChunkContainerRef.read(inStream, parent)
            return when (refType) {
                EntryRef.RefType.CHUNK_CONTAINER -> ChunkContainerEntryRef(containerRef)
                EntryRef.RefType.CC_REV_LOG -> RevLogEntryRef.read(containerRef, inStream)
            }
        }
    }

    open suspend fun write(outStream: AsyncOutStream): Int {
        var bytesWritten = 0
        bytesWritten += outStream.writeByte(type.value.toByte())
        bytesWritten += containerRef.write(outStream)
        return bytesWritten
    }
}


class ChunkContainerEntryRef(containerRef: ChunkContainerRef) : EntryRef(RefType.CHUNK_CONTAINER, containerRef)

class RevLogEntryRef(containerRef: ChunkContainerRef) : EntryRef(RefType.CC_REV_LOG, containerRef) {
    // [n RevLogEntries
    // List of hash, offset pairs:
    // |32) hash|
    // [offset]

    companion object {
        suspend fun read(containerRef: ChunkContainerRef, inStream: AsyncInStream)
                : RevLogEntryRef {
            var revLogRef = RevLogEntryRef(containerRef)

            val nEntries = VarInt.read(inStream).first
            (0 until nEntries).forEach {
                val hash = Hash.read(inStream, containerRef.hash.spec)
                val offset = VarInt.read(inStream).first
                revLogRef.versions.add(hash to offset)
            }
            return revLogRef
        }

    }

    override suspend fun write(outStream: AsyncOutStream): Int {
        var bytesWritten = super.write(outStream)

        bytesWritten += VarInt.write(outStream, versions.size)
        versions.forEach {
            bytesWritten += it.first.write(outStream)
            bytesWritten += VarInt.write(outStream, it.second)
        }
        return bytesWritten
    }

    val versions: MutableList<Pair<Hash, Long>> = ArrayList()

    fun add(hash: Hash, position: Long) {
        versions.add(0, hash to position)
    }

    override fun has(hash: Hash): Boolean {
        return versions.firstOrNull { it.first == hash} != null
    }

    override fun getHashes(): List<Hash> {
        return versions.map { it.first }
    }
}


class ObjectIndexEntryList(val chunkContainer: ChunkContainer, var startOffset: Long = 0,
                           val directory: EntryDirectory = EntryDirectory()) {
    companion object {
        suspend fun read(chunkContainer: ChunkContainer, startOffset: Long): ObjectIndexEntryList {
            if (startOffset == chunkContainer.getDataLength())
                return ObjectIndexEntryList(chunkContainer, startOffset, EntryDirectory())

            val inStream = ChunkContainerInStream(chunkContainer)
            inStream.seek(startOffset)
            val dir = EntryDirectory.read(inStream)

            return ObjectIndexEntryList(chunkContainer, startOffset, dir)
        }
    }

    val dataOffset: Long
        get() = startOffset + directory.directorySize

    fun printToString(): String {
        var out = "Directory: startOffset = $startOffset dirSize = ${directory.directorySize} " +
                "dataOffset = $dataOffset " +
                "contentSize = ${directory.contentSize} (${dataOffset + directory.contentSize})\n"

        for (header in directory.headers) {
            out += "\t${header.first.id}:\t length = ${header.first.length}, pos = ${header.second}\n"
        }
        return out
    }

    fun listEntries(): Collection<String> {
        return directory.headers.map { it.first.id }
    }

    suspend fun listChunkContainerRefs(): List<Pair<String, ChunkContainerRef>> {
        return listEntries().mapNotNull {
            val id = it
            get(id)?.entries?.map { id to it.containerRef }
        }.flatten()
    }

    suspend fun remove(id: String): EntryRefList? {
        val entry = directory.remove(id) ?: return null

        val stream = ChunkContainerRandomDataAccess(chunkContainer,
                RandomDataAccess.Mode.WRITE.add(RandomDataAccess.Mode.READ))
        stream.seek(entry.second)
        val entryRefList = EntryRefList.read(stream, chunkContainer.ref.hash.spec)
        stream.delete(entry.second, entry.first.length.toLong())
        stream.close()
        return entryRefList
    }

    suspend fun update(id: String, entry: EntryRefList) {
        val randomDataAccess = ChunkContainerRandomDataAccess(chunkContainer, RandomDataAccess.Mode.INSERT)

        val index = directory.indexOf(id)
        if (index >= 0) {
            // overwrite
            val oldEntry = directory.headers[index]
            randomDataAccess.delete(dataOffset + oldEntry.second, oldEntry.first.length.toLong())
            randomDataAccess.seek(dataOffset + oldEntry.second)
            val entrySize = entry.write(randomDataAccess)

            // update index
            directory.update(index, entrySize)
        } else {
            // insert
            val insertPos = (index * -1) - 1
            val dataPos = if (insertPos == directory.size()) directory.contentSize
                                    else directory.headers[insertPos].second

            randomDataAccess.seek(dataOffset + dataPos)
            val entrySize = entry.write(randomDataAccess)

            directory.insert(insertPos, id, entrySize)
        }
        randomDataAccess.close()
    }

    suspend fun get(id: String): EntryRefList? {
        val index = directory.indexOf(id)
        if (index < 0)
            return null
        val dataPos = directory.headers[index].second
        val inputStream = ChunkContainerInStream(chunkContainer)
        inputStream.seek(dataOffset + dataPos)
        return EntryRefList.read(inputStream, chunkContainer.ref.hash.spec)
    }

    suspend fun write(outputStream: ChunkContainerRandomDataAccess) {
        outputStream.delete(startOffset, directory.directorySize)
        outputStream.seek(startOffset)
        directory.write(outputStream)
    }

    // |Directory|
    // |Entries|
    class EntryDirectory {
        // [n Entries]
        // |EntryHeaders|

        var directorySize = 0L
        var contentSize = 0L
        val headers: MutableList<Pair<ObjectIndex.EntryHeader, Long>> = ArrayList()

        fun indexOf(id: String): Int {
            return headers.map { it.first.id }.binarySearch(id)
        }

        fun remove(id: String): Pair<ObjectIndex.EntryHeader, Long>? {
            val index = indexOf(id)
            if (index < 0)
                return null
            return headers.removeAt(index)
        }

        /**
         * @return the insert position in the entry block
         */
        fun insert(index: Int, id: String, size: Int): Long {
            contentSize += size
            val insertPos = if (index == 0) 0L else headers[index - 1].let { it.second + it.first.length }
            headers.add(index,ObjectIndex.EntryHeader(id, size) to insertPos)
            offset(index, size)
            return insertPos
        }

        /**
         * Shift the position of all EntryHeader with index > after by offset.
         */
        private fun offset(after: Int, offset: Int) {
            for (i in after + 1 until headers.size) {
                val current = headers.removeAt(i)
                headers.add(i, current.first to current.second + offset)
            }
        }

        fun update(index: Int, newEntryLength: Int) {
            val pair = headers.removeAt(index)
            val entry = pair.first
            val oldLength = entry.length
            entry.length = newEntryLength
            contentSize -= oldLength
            contentSize += newEntryLength

            headers.add(index, entry to pair.second)

            val diff = newEntryLength - oldLength
            offset(index, diff)
        }

        companion object {
            suspend fun read(inStream: AsyncInStream): EntryDirectory {
                val entryIndex = EntryDirectory()
                try {
                    val size = VarInt.read(inStream).let {
                        entryIndex.directorySize = it.second.toLong()
                        return@let it.first.toInt()
                    }
                    for (i in 0 until size) {
                        val result = ObjectIndex.EntryHeader.read(inStream)
                        entryIndex.insert(i, result.first.id, result.first.length)
                        entryIndex.directorySize += result.second
                    }
                    return entryIndex
                } catch (e: EOFException) {
                    // empty index
                    return entryIndex
                }
            }
        }

        fun size(): Int {
            return headers.size
        }

        suspend fun write(outStream: AsyncOutStream): Long {
            directorySize = VarInt.write(outStream, size()).toLong()

            headers.forEach {
                directorySize += it.first.write(outStream)
            }
            return directorySize
        }
    }

}


class ObjectIndex private constructor(val config: RepositoryConfig, val chunkContainer: ChunkContainer,
                                      var version: Version, var parent: ChunkContainerRef,
                                      val entries: ObjectIndexEntryList) {
    enum class Version(val value: Int) {
        V1(1)
    }

    companion object {
        val COMMIT_ID = "commit"
        val TREE_ID = "tree"
        val BLOB_ID = "b/"

        fun create(config: RepositoryConfig, chunkContainer: ChunkContainer): ObjectIndex {
            return ObjectIndex(config, chunkContainer, Version.V1,
            ChunkContainerRef(config.hashSpec, config.boxSpec), ObjectIndexEntryList(chunkContainer))
        }

        suspend fun open(config: RepositoryConfig, chunkContainer: ChunkContainer): ObjectIndex {
            val inputStream = ChunkContainerRandomDataAccess(chunkContainer, RandomDataAccess.Mode.READ)

            val versionValue = inputStream.read()
            val version = Version.values().firstOrNull { it.value == versionValue }
                    ?: throw IOException("Unknown version $versionValue")
            val parent = ChunkContainerRef.read(inputStream, chunkContainer.ref.hash.spec)
            val recentEntries = ObjectIndexEntryList.read(chunkContainer, inputStream.position())
            inputStream.close()

            return ObjectIndex(config, chunkContainer, version, parent, recentEntries)
        }
    }

    suspend fun printToString(deep: Boolean): String {
        var out = "Version: ${version.value}\n"
        out += "Parent: ${parent.hash.value}\n"
        out += entries.printToString()
        if (!deep)
            return out

        out += "Content:\n"
        entries.directory.headers.forEach {
            val headerEntry = it.first
            val entry = entries.get(headerEntry.id) ?: throw Exception("Invalid entry: $it")
            out += "${headerEntry.id} length: ${headerEntry.length}, position: ${it.second}\n"
            out += entry.printToString() + "\n"
        }
        return out
    }

    suspend private fun writeHeaderAndEntryHeaders() {
        val outputStream = ChunkContainerRandomDataAccess(chunkContainer, RandomDataAccess.Mode.INSERT)
        outputStream.delete(0, entries.startOffset)

        outputStream.seek(0)
        outputStream.writeByte(version.value.toByte())
        parent.write(outputStream)

        entries.startOffset = outputStream.position()
        entries.write(outputStream)
        outputStream.close()
    }

    suspend fun listChunkContainers(): List<Pair<String, ChunkContainerRef>> {
        return entries.listChunkContainerRefs()
    }

    suspend fun getBlob(path: String, hash: Hash): ChunkContainer? {
        return getChunkContainer("$BLOB_ID$path", hash)
    }

    suspend fun putBlob(path: String, data: ChunkContainer): Hash {
        return putChunkContainer("$BLOB_ID$path", data)
    }

    suspend fun getCommitChunkContainer(hash: Hash): ChunkContainer? {
        return getChunkContainer(COMMIT_ID, hash)
    }

    suspend fun getCommitEntries(): Collection<Hash> {
        return getEntries(COMMIT_ID)
    }

    suspend fun putCommit(commit: ChunkContainer): Hash {
        return putChunkContainer(COMMIT_ID, commit)
    }

    suspend fun getDirChunkContainer(hash: Hash): ChunkContainer? {
        return getChunkContainer(TREE_ID, hash)
    }

    suspend fun putDir(dir: ChunkContainer): Hash {
        return putChunkContainer(TREE_ID, dir)
    }

    suspend private fun getEntries(path: String): List<Hash> {
        val entryRefList = entries.get(path) ?: return emptyList()
        return entryRefList.entries.flatMap { it.getHashes() }
    }

    fun getChunkAccessor(ref: ChunkContainerRef): ChunkAccessor {
        return when (ref.boxSpec.encInfo.type) {
            BoxSpec.EncryptionInfo.Type.PARENT -> chunkContainer.blobAccessor
            BoxSpec.EncryptionInfo.Type.PLAIN -> TODO("We need access to the plain chunk accessor")
        }
    }

    suspend private fun getChunkContainer(id: String, hash: Hash): ChunkContainer? {
        val entryRefList = entries.get(id) ?: return null
        val entryRef = entryRefList.getRefFor(hash) ?: return null
        return getChunkContainer(entryRef, hash)
    }

    suspend private fun getChunkContainer(entryRef: EntryRef, hash: Hash)
            : ChunkContainer? {
        val containerRef = entryRef.containerRef
        val container = ChunkContainer.read(getChunkAccessor(containerRef), containerRef)
        return when (entryRef.type) {
            EntryRef.RefType.CHUNK_CONTAINER -> {
                return container
            }
            EntryRef.RefType.CC_REV_LOG -> {
                val revLogEntryRef = entryRef as RevLogEntryRef
                val version = revLogEntryRef.versions.firstOrNull { it.first == hash } ?: return null
                val revLog = Revlog(container)
                val data = revLog.get(version.second)
                // use rev log container spec
                val newContainerRef = ChunkContainerRef(containerRef.hash.spec, containerRef.boxSpec)
                val newContainer = ChunkContainer.create(getChunkAccessor(newContainerRef),
                        newContainerRef.containerSpec)
                val outStream = ChunkContainerOutStream(newContainer)
                outStream.write(data)
                outStream.close()
                return newContainer
            }
        }
    }

    /**
     * Get the EntryRefList for the given id and move it into recent if it is in the past section.
     */
    suspend private fun makeActive(id: String): EntryRefList {
        return entries.get(id)?.also { return it }?.also {
            // move to recent list
            entries.update(id, it)
        } ?: EntryRefList()
    }

    suspend private fun putChunkContainer(id: String, chunkContainer: ChunkContainer): Hash {
        chunkContainer.flush(false)
        val hash = chunkContainer.ref.hash
        val entryRefList = makeActive(id)

        // Don't add an entry if it already exists
        if (entryRefList.entries.firstOrNull { it.has(hash) } != null)
            return hash

        if (entryRefList.entries.size == 0 || chunkContainer.size() > config.revLog.maxEntrySize) {
            // add as a chunk container
            entryRefList.push(ChunkContainerEntryRef(chunkContainer.ref))
            entries.update(id, entryRefList)
            return hash
        }

        // find diff
        // TODO perform a more sorrow search for diff candidates
        val entryRef = entryRefList.entries[0]
        val base = getChunkContainer(entryRef, entryRef.getHashes()[0])
                ?: throw Exception("Expected to load ChunkContainer")
        if (base.size() > config.revLog.maxEntrySize) {
            // don't diff just add as a chunk container
            entryRefList.push(ChunkContainerEntryRef(chunkContainer.ref))
            entries.update(id, entryRefList)
            return hash
        }

        when (entryRef.type) {
            EntryRef.RefType.CHUNK_CONTAINER -> {
                // transform chunk container to rev log
                val newContainerRef = ChunkContainerRef(ContainerSpec(config.hashSpec, config.boxSpec))
                val newContainer = ChunkContainer.create(getChunkAccessor(newContainerRef),
                        newContainerRef.containerSpec)
                val revLog = Revlog(newContainer)
                val basePos = revLog.add(ChunkContainerInStream(base).readAll())
                val newPos = revLog.add(ChunkContainerInStream(chunkContainer).readAll())
                newContainer.flush(false)
                val revLogEntry = RevLogEntryRef(newContainer.ref)
                revLogEntry.add(base.ref.hash, basePos)
                revLogEntry.add(hash, newPos)
                entryRefList.replace(entryRef, revLogEntry)
                entries.update(id, entryRefList)
                return hash
            }
            EntryRef.RefType.CC_REV_LOG -> {
                val revLogEntry = entryRef as RevLogEntryRef
                val containerRef = entryRef.containerRef
                val revLogContainer = ChunkContainer.read(getChunkAccessor(containerRef), containerRef)
                val revLog = Revlog(revLogContainer)
                val newPos = revLog.add(ChunkContainerInStream(chunkContainer).readAll())
                base.flush(false)
                revLogEntry.add(hash, newPos)
                entries.update(id, entryRefList)
                return hash
            }
        }
    }



    suspend fun flush(): ChunkContainerRef {
        writeHeaderAndEntryHeaders()
        chunkContainer.flush(false)
        return chunkContainer.ref
    }


    // General Format has three main blocks:
    // --header--
    // |Version|
    // |ChunkContainerRef|
    // |Recent EntryList|
    // |Past EntryList|

    class EntryHeader(val id: String, var length: Int) {
        // {entry id}
        // [Entry length]

        companion object {
            suspend fun read(inStream: AsyncInStream): Pair<EntryHeader, Int> {
                val idResult = inStream.readVarIntDelimited()
                val id = idResult.first.toUTFPath()
                val lengthResult = VarInt.read(inStream)
                val length = lengthResult.first.toInt()
                return EntryHeader(id, length) to idResult.second + lengthResult.second
            }
        }

        suspend fun write(outStream: AsyncOutStream): Int {
            var bytesWritten = 0
            bytesWritten += outStream.writeVarIntDelimited(id.toUTFPath())
            bytesWritten += VarInt.write(outStream, length)
            return bytesWritten
        }
    }
}