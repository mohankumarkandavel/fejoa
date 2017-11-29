package org.fejoa.repository

import org.fejoa.chunkcontainer.Hash
import org.fejoa.support.IOException


class TreeAccessor(val root: Directory, private var transaction: ChunkAccessors.Transaction) {
    var isModified = false
        private set

    fun setTransaction(transaction: ChunkAccessors.Transaction) {
        this.transaction = transaction
    }

    private fun checkPath(path: String): String {
        var path = path
        while (path.startsWith("/"))
            path = path.substring(1)
        return path
    }

    operator fun get(path: String): DirectoryEntry? {
        var path = path
        path = checkPath(path)
        val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.isEmpty())
            return root
        val entryName = parts[parts.size - 1]
        val currentDir = get(parts, parts.size - 1, false) ?: return null
        return if (entryName == "") currentDir else currentDir.getEntry(entryName)
    }

    /**
     * @param parts List of directories
     * @param nDirs Number of dirs in parts that should be follow
     * @return null or an entry pointing to the request directory, the object is loaded
     * @throws IOException
     * @throws CryptoException
     */
    operator fun get(parts: Array<String>, nDirs: Int, invalidateTouchedDirs: Boolean): Directory? {
        var currentDir: Directory = root
        if (invalidateTouchedDirs)
            currentDir.markModified()
        for (i in 0 until nDirs) {
            val subDir = parts[i]
            val entry = currentDir.getEntry(subDir) ?: return null
            if (entry.isFile())
                return null

            if (invalidateTouchedDirs)
                entry.markModified()

            currentDir = entry as Directory
        }
        if (currentDir === root) {
            if (invalidateTouchedDirs)
                currentDir.markModified()
        }
        return currentDir
    }

    fun putBlob(path: String, hash: Hash): BlobEntry {
        val result = prepareInsertDirectory(path)
        val parent = result.first
        val fileName = result.second
        val existing = parent.getEntry(fileName)
        if (existing != null && !existing.isFile())
            throw Exception("Directory at insert path")
        val blob = BlobEntry(fileName, hash)
        this.isModified = true
        parent.put(blob)
        return blob
    }

    fun put(path: String, entry: DirectoryEntry) {
        val result = prepareInsertDirectory(path)
        entry.name = result.second
        this.isModified = true
        result.first.put(entry)
    }

    private fun prepareInsertDirectory(path: String): Pair<Directory, String> {
        var path = path
        path = checkPath(path)
        val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val fileName = parts[parts.size - 1]
        var currentDir = root
        val touchedEntries = ArrayList<DirectoryEntry>()
        touchedEntries.add(currentDir)
        for (i in 0 until parts.size - 1) {
            val subDir = parts[i]
            var currentEntry = currentDir.getEntry(subDir)
            if (currentEntry == null) {
                currentEntry = Directory(subDir)
                currentDir.put(currentEntry)
                currentDir = currentEntry
            } else {
                if (currentEntry.isFile())
                    throw IOException("Invalid insert path: " + path)
                currentDir = currentEntry as Directory
            }
            touchedEntries.add(currentEntry)
        }
        for (touched in touchedEntries)
            touched.markModified()
        return currentDir to fileName
    }

    fun remove(path: String): DirectoryEntry? {
        var path = checkPath(path)
        this.isModified = true
        val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val entryName = parts[parts.size - 1]
        val currentDir = get(parts, parts.size - 1, true) ?: return null
        // invalidate entry
        return currentDir.remove(entryName)
    }

    suspend fun build(): Hash {
        isModified = false
        root.flush()
        return root.hash.clone()
    }
}
