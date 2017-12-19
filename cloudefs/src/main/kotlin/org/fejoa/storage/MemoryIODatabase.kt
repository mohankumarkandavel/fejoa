package org.fejoa.storage

import org.fejoa.support.*

import kotlin.math.max



internal class MemoryRandomDataAccess(buffer: ByteArray, mode: RandomDataAccess.Mode, private val callback: IIOCallback) : RandomDataAccess {
    private var isOpen = true
    var data: ByteArray = buffer
        private set
    private var position = 0
    private var inStream: ByteArrayInStream? = null
    private var outputStream: ByteArrayOutStream? = null
    override val mode: RandomDataAccess.Mode = mode

    internal interface IIOCallback {
        fun onClose(that: MemoryRandomDataAccess)
    }

    override fun length(): Long {
        return if (outputStream != null) max(position, outputStream!!.toByteArray().size).toLong() else data!!.size.toLong()
    }

    override fun position(): Long {
        return position.toLong()
    }

    suspend override fun seek(position: Long) {
        if (inStream != null)
        // position is set on next read
            inStream = null
        else
            flush()
        this.position = position.toInt()
    }

    suspend override fun write(data: ByteArray, offset: Int, length: Int): Int {
        if (!mode.has(RandomDataAccess.Mode.WRITE))
            throw IOException("Read only")
        if (inStream != null)
            inStream = null
        if (outputStream == null) {
            outputStream = ByteArrayOutStream()
            outputStream!!.write(this.data!!, 0, position)
        }
        outputStream!!.write(data, offset, length)
        position += length
        return length
    }

    suspend override fun delete(position: Long, length: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    suspend override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!mode.has(RandomDataAccess.Mode.READ))
            throw IOException("Not in read mode")

        if (outputStream != null) {
            flush()
        }
        if (inStream == null)
            inStream = ByteArrayInStream(this.data, position)

        val read = inStream!!.read(buffer, offset, length)
        position += read
        return read
    }

    suspend override fun flush() {
        if (outputStream == null)
            return
        // if we didn't overwrite the whole buffer copy the remaining bytes
        if (position < this.data.size)
            outputStream!!.write(data, position, data.size - position)
        this.data = outputStream!!.toByteArray()
        outputStream = null
    }

    suspend override fun truncate(size: Long) {
        if (length() <= size)
            return
        flush()
        if (length() > size)
            data = data!!.copyOfRange(0, size.toInt())

        if (position > size)
            position = size.toInt()
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    suspend override fun close() {
        isOpen = false
        flush()
        callback.onClose(this)
    }
}

class MemoryIODatabase : IODatabase {
    internal val root = Dir(null, "")

    val entries: Map<String, ByteArray>
        get() {
            val out = HashMap<String, ByteArray>()
            getEntries(out, root, "")
            return out
        }

    internal class Dir(private val parent: Dir?, private val name: String) {
        val dirs = HashMap<String, Dir>()
        val files = HashMap<String, ByteArray>()

        fun getSubDir(dir: String, createMissing: Boolean): Dir? {
            var subDir = this
            if (dir == "")
                return subDir
            val parts = dir.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (part in parts) {
                val subSubDir = subDir.dirs[part]
                if (subSubDir == null) {
                    if (!createMissing)
                        return null
                    val newSubDir = Dir(subDir, part)
                    subDir.dirs.put(part, newSubDir)
                    subDir = newSubDir
                } else
                    subDir = subSubDir
            }
            return subDir
        }

        fun put(path: String, data: ByteArray) {
            val dirPath = PathUtils.dirName(path)
            val fileName = PathUtils.fileName(path)
            val subDir = getSubDir(dirPath, true)
            subDir!!.files.put(fileName, data)
        }

        operator fun get(path: String): ByteArray? {
            val dirPath = PathUtils.dirName(path)
            val fileName = PathUtils.fileName(path)
            val subDir = getSubDir(dirPath, false) ?: return null
            return subDir.files[fileName]
        }

        fun probe(path: String): IODatabase.FileType {
            val dirPath = PathUtils.dirName(path)
            val fileName = PathUtils.fileName(path)
            val subDir = getSubDir(dirPath, false) ?: return IODatabase.FileType.NOT_EXISTING
            if (subDir.dirs.containsKey(fileName))
                return IODatabase.FileType.DIRECTORY
            return if (subDir.files.containsKey(fileName)) IODatabase.FileType.FILE else IODatabase.FileType.NOT_EXISTING
        }

        fun remove(path: String) {
            val dirPath = PathUtils.dirName(path)
            val fileName = PathUtils.fileName(path)
            var subDir: Dir? = getSubDir(dirPath, false) ?: return
            subDir!!.files.remove(fileName)
            while (subDir!!.files.size == 0 && subDir.dirs.size == 0 && subDir.parent != null) {
                val parent = subDir.parent
                parent!!.dirs.remove(subDir.name)
                subDir = parent
            }
        }
    }

    suspend override fun probe(path: String): IODatabase.FileType {
        var path = path
        path = validate(path)
        return root.probe(path)
    }

    fun hasFile(path: String): Boolean {
        var path = path
        path = validate(path)
        return root.probe(path) === IODatabase.FileType.FILE
    }

    suspend override fun getHash(path: String): HashValue {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun readBytesInternal(path: String): ByteArray? {
        var path = path
        path = validate(path)
        return root[path]
    }

    private fun getList(map: MutableMap<String, List<String>>, path: String): List<String> {
        var list: List<String>? = map[path]
        if (list == null) {
            list = ArrayList<String>()
            map.put(path, list)
        }
        return list
    }

    private fun validate(path: String): String {
        var path = path
        while (path.length > 0 && path[0] == '/')
            path = path.substring(1)
        return path
    }

    suspend override fun readBytes(path: String): ByteArray {
        return readBytesInternal(path) ?: throw IOException("Not found!")

        /* ISyncRandomDataAccess dataAccess = open(path, Mode.READ);
        byte[] data = StreamHelper.readAll(dataAccess);
        dataAccess.close();
        return data;*/
    }

    suspend override fun putBytes(path: String, bytes: ByteArray) {
        root.put(validate(path), bytes)
        /*ISyncRandomDataAccess dataAccess = open(path, Mode.TRUNCATE);
        dataAccess.write(bytes);
        dataAccess.close();*/
    }

    suspend override fun open(path: String, mode: RandomDataAccess.Mode): RandomDataAccess {
        var existingBytes = readBytesInternal(path)
        if (existingBytes == null) {
            if (!mode.has(RandomDataAccess.Mode.WRITE))
                throw FileNotFoundException("File not found: " + path)
            existingBytes = ByteArray(0)
        }
        return MemoryRandomDataAccess(existingBytes, mode, object : MemoryRandomDataAccess.IIOCallback {
            override fun onClose(that: MemoryRandomDataAccess) {
                if (!that.mode.has(RandomDataAccess.Mode.WRITE))
                    return
                root.put(validate(path), that.data)
            }
        })
    }

    suspend override fun remove(path: String) {
        var path = path
        path = validate(path)
        root.remove(path)
    }

    suspend override fun listFiles(path: String): Collection<String> {
        val parentDir = root.getSubDir(path, false) ?: return emptyList()
        return parentDir.files.keys
    }

    suspend override fun listDirectories(path: String): Collection<String> {
        val parentDir = root.getSubDir(path, false) ?: return emptyList()
        return parentDir.dirs.keys
    }

    private fun getEntries(out: MutableMap<String, ByteArray>, dir: Dir, path: String) {
        for ((key, value) in dir.files)
            out.put(PathUtils.appendDir(path, key), value)
        for ((key, value) in dir.dirs)
            getEntries(out, value, PathUtils.appendDir(path, key))
    }
}
