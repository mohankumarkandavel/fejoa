package org.fejoa.repository

import org.fejoa.chunkcontainer.ChunkContainerInStream
import org.fejoa.chunkcontainer.Hash
import org.fejoa.chunkcontainer.HashSpec
import org.fejoa.protocolbufferlight.VarInt
import org.fejoa.repository.DirectoryEntry.EntryType.*
import org.fejoa.storage.*
import org.fejoa.support.*


suspend fun AsyncOutStream.writeVarIntDelimited(byteArray: ByteArray): Int {
    var bytesWritten= VarInt.write(this, byteArray.size)
    write(byteArray)
    bytesWritten += byteArray.size
    return bytesWritten
}

suspend fun AsyncInStream.readVarIntDelimited(): Pair<ByteArray, Int> {
    val result  = VarInt.read(this)
    var read = result.second
    val length = result.first
    if (length > 10 * 1000 * 1024)
        throw IOException("Refuse to read large ByteArray")
    val buffer = ByteArray(length.toInt())
    read(buffer)
    return buffer to read + buffer.size
}


// Legend:
// [i] denotes a VarInt
// |l) data| denotes some data of length l
// {data} denotes variable sized data, the size the data (as a VarInt) is prepended to the data: [l]|l)data|


abstract class DirectoryEntry(val entryType: EntryType, val nameAttrData: NameAttrData, val hash: Hash) {
    enum class EntryType(val value: Int) {
        SYMBOLIC_LINK(1),
        HARD_LINK(2),
        LAST_DIR_ENTRY(10), // end of directory entries
        EMPTY_DIR(11),
        DIR(12),
        BLOB(20),
    }

    var name: String
        get() = nameAttrData.name
        set(value) { nameAttrData.name = value }

    override fun toString(): String {
        return name
    }

    // |EntryType (1|
    open suspend fun write(outStream: AsyncOutStream) {
        outStream.writeByte(entryType.value.toByte())
    }

    fun isFile(): Boolean = entryType == BLOB
    fun isDir(): Boolean = !isFile()

    fun markModified() {
        hash.value = Config.newDataHash()
    }

    fun isModified(): Boolean = hash.value.isZero

    suspend fun flush(): HashValue {
        if (!isModified())
            return hash.value

        hash.value = flushImplementation()
        return hash.value
    }

    abstract suspend protected fun flushImplementation(): HashValue
}


class Directory(nameAttrData: NameAttrData, hash: Hash) : DirectoryEntry(DIR, nameAttrData, hash) {
    constructor(name: String, parent: HashSpec) : this(NameAttrData(name), Hash.createChild(parent))

    private val children: MutableList<DirectoryEntry> = ArrayList()

    fun getChildren(): List<DirectoryEntry> {
        return children
    }

    enum class DirType(val value: Int) {
        FLAT_DIR(1)
    }

    companion object {
        suspend fun readRoot(hash: Hash, objectIndex: ObjectIndex): Directory {
            val container = objectIndex.getDirChunkContainer(hash)
                    ?: throw Exception("Can't find dir ${hash.value}")
            val root = readRoot(ChunkContainerInStream(container), hash.spec.createChild())
            if (hash.value != root.hash())
                throw Exception("Unexpected directory hash")
            return root
        }

        suspend private fun readRoot(inStream: AsyncInStream, parent: HashSpec): Directory {
            val type = inStream.readByte().toInt()
            if (type != DirType.FLAT_DIR.value)
                throw Exception("Unsupported directory type: $type")

            val dir = Directory(NameAttrData(""), Hash.createChild(parent))
            readChildren(dir, inStream, parent)
            return dir
        }

        suspend private fun readChildren(parent: Directory, inStream: AsyncInStream, parentSpec: HashSpec) {
            while (true) {
                val typeValue = inStream.readByte().toInt()
                val type = values().firstOrNull { it.value == typeValue }
                        ?: throw IOException("Unknown dir entry type: $typeValue")
                if (type == LAST_DIR_ENTRY)
                    break
                parent.children.add(readSingleEntry(type, inStream, parentSpec))
            }
        }

        suspend private fun readSingleEntry(type: EntryType, inStream: AsyncInStream, parent: HashSpec)
                : DirectoryEntry {
            return when (type) {
                LAST_DIR_ENTRY -> throw Exception("Should't not happen")
                EMPTY_DIR,
                DIR -> {
                    val nameAttrData = NameAttrData.read(inStream)
                    val dir = Directory(nameAttrData, Hash.createChild(parent))
                    if (type != EMPTY_DIR)
                        readChildren(dir, inStream, parent)
                    dir
                }
                BLOB -> BlobEntry.read(inStream, parent)
                SYMBOLIC_LINK,
                HARD_LINK -> TODO()
            }
        }
    }

    fun getEntry(name: String): DirectoryEntry? {
        return children.firstOrNull { it.name == name }
    }

    fun put(entryBase: DirectoryEntry) {
        getEntry(entryBase.name)?.let { children.remove(it) }
        children.add(entryBase)
    }

    fun remove(name: String): DirectoryEntry? {
        val index = children.indexOfFirst { it.name == name }
        if (index < 0)
            return null
        return children.removeAt(index)
    }

    suspend fun writeRoot(outStream: AsyncOutStream) {
        outStream.write(DirType.FLAT_DIR.value)
        writeChildren(outStream)
    }

    suspend fun writeChildren(outStream: AsyncOutStream) {
        children.sortBy { it.name }

        for (child in children)
            child.write(outStream)

        outStream.writeByte(LAST_DIR_ENTRY.value.toByte())
    }

    override suspend fun write(outStream: AsyncOutStream) {
        if (children.size == 0) {
            outStream.writeByte(EMPTY_DIR.value.toByte())
            nameAttrData.write(outStream)
        } else {
            outStream.writeByte(DIR.value.toByte())
            nameAttrData.write(outStream)
            writeChildren(outStream)
        }
    }

    suspend private fun hash(): HashValue {
        val hashOutStream = hash.spec.getHashOutStream()
        val outStream: AsyncOutStream = AsyncHashOutStream(AsyncByteArrayOutStream(), hashOutStream)

        writeRoot(outStream)

        outStream.close()
        hash.value = HashValue(hashOutStream.hash())
        return hash.value
    }

    override suspend fun flushImplementation(): HashValue {
        children.forEach {
            it.hash.value = it.flush()
        }
        return hash()
    }
}

suspend fun Directory.getDiff(theirs: Directory, includeAllAdded: Boolean, includeAllRemoved: Boolean)
        : TreeIterator {
    return TreeIterator(this, theirs = theirs, includeAllAdded = includeAllAdded,
            includeAllRemoved = includeAllRemoved)
}

suspend fun Directory.getDiffEntries(theirs: Directory, includeAllAdded: Boolean, includeAllRemoved: Boolean)
        : Collection<DatabaseDiff.Entry> {
    val changes = ArrayList<DatabaseDiff.Entry>()
    val diffIterator = TreeIterator(this, theirs = theirs, includeAllAdded = includeAllAdded,
            includeAllRemoved = includeAllRemoved)
    while (diffIterator.hasNext()) {
        val change = diffIterator.next()
        when (change.type) {
            DiffIterator.Type.MODIFIED ->
                // filter out directories
                if (change.ours!!.isFile() || change.theirs!!.isFile())
                    changes.add(DatabaseDiff.Entry(change.path, DatabaseDiff.ChangeType.MODIFIED))

            DiffIterator.Type.ADDED -> changes.add(DatabaseDiff.Entry(change.path, DatabaseDiff.ChangeType.ADDED))

            DiffIterator.Type.REMOVED -> changes.add(DatabaseDiff.Entry(change.path, DatabaseDiff.ChangeType.REMOVED))
        }
    }

    return changes
}

class BlobEntry(nameAttrData: NameAttrData, hash: Hash) : DirectoryEntry(BLOB, nameAttrData, hash) {
    constructor(name: String, hash: Hash) : this(NameAttrData(name), hash)

    companion object {
        suspend fun read(inStream: AsyncInStream, parent: HashSpec?): BlobEntry {
            val nameAttrData = NameAttrData.read(inStream)
            val hash = Hash.read(inStream, parent)
            return BlobEntry(nameAttrData, hash)
        }
    }

    override suspend fun write(outStream: AsyncOutStream) {
        super.write(outStream)

        nameAttrData.write(outStream)
        hash.write(outStream)
    }

    suspend override fun flushImplementation(): HashValue {
        return hash.value
    }
}


class NameAttrData(var name: String) {
    val attributeMap: MutableMap<Int, ByteArray> = HashMap()

    enum class AttrFlags(val value: Int) {
        FS_ATTR(1 shl 0),
        EXTENDED_ATTR_DIR(1 shl 1),// TODO: attributes stored in a separate object
        EXTENDED_ATTR_LOCAL(1 shl 2), // TODO: attributes stored in the dir entry
    }

    // {name}
    // |Attribute Flags (1| // 8 types of attribute data
    // {Basic FS attributes (optional)}
    // {Extended attributes (optional EntryHash)} // refers another object
    companion object {
        suspend fun read(inStream: AsyncInStream): NameAttrData {
            val name = inStream.readVarIntDelimited().first.toUTFPath()
            val data = NameAttrData(name)
            val attributeFlags = inStream.readByte().toInt()
            for (i in 0 until 8) {
                if (attributeFlags and (1 shl 0) != 0)
                    data.attributeMap[i] = inStream.readVarIntDelimited().first
            }
            return data
        }
    }

    suspend fun write(outStream: AsyncOutStream) {
        outStream.writeVarIntDelimited(name.toUTFPath())

        // build attr flag byte
        val attributes = (0 until 8).mapNotNull { attributeMap[it] }
                .mapIndexed { index, data -> index to data}
        var attrFlags = 0
        attributes.forEach {
            attrFlags = attrFlags or (1 shl it.first)
        }
        outStream.writeByte(attrFlags.toByte())
        attributes.forEach {
            outStream.writeVarIntDelimited(it.second)
        }
    }
}



