package org.fejoa.repository

import kotlinx.serialization.SerialContext
import kotlinx.serialization.json.JSON
import org.fejoa.chunkstore.LockBucket
import org.fejoa.storage.HashValue
import org.fejoa.storage.HashValueDataSerializer
import org.fejoa.support.Future
import org.fejoa.support.Future.Companion.completedFuture

import java.io.*
import java.util.concurrent.locks.Lock


/**
 * There are two parts:
 * 1) log: contains a list of previous versions
 * 2) head: points to the latest or topmost commit
 */
class ChunkStoreBranchLog(logDir: File, branch: String, remote: String? = null) : BranchLog {
    private var latestRev = 1
    private val headFile: File
    private val logFile: File
    private val fileLock: Lock
    private var entries: BranchLogList? = null

    init {
        var logDir = logDir

        if (remote == null)
            logDir = getLocalDir(logDir)
        else
            logDir = File(File(logDir, "remote"), remote)


        val branchDir = File(logDir, branch)
        this.fileLock = LockBucket.getInstance().getLock(branchDir.absolutePath)

        headFile = File(branchDir, "head")
        logFile = File(branchDir, "log")
    }

    private fun lock() {
        fileLock.lock()
    }

    private fun unlock() {
        fileLock.unlock()
    }

    @Throws(IOException::class)
    override fun getEntries(): Future<List<BranchLogEntry>> {
        if (entries == null)
            entries = readLogs()

        return completedFuture(entries!!.entries)
    }

    @Throws(IOException::class)
    private fun readLogs(): BranchLogList{
        try {
            lock()
            // read log
            try {
                val fileInputStream = FileInputStream(logFile)
                val reader = BufferedReader(InputStreamReader(fileInputStream))
                val text = reader.readText()
                val context = SerialContext().apply {
                    registerSerializer(HashValue::class, HashValueDataSerializer)
                }
                return JSON(context = context).parse(text)
            } catch (e: FileNotFoundException) {
                return BranchLogList()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        } finally {
            unlock()
        }
    }

    private fun nextRevId(): Int {
        val currentRev = latestRev
        latestRev++
        return currentRev
    }

    override fun getHead(): Future<BranchLogEntry?> {
        if (entries == null)
            entries = readLogs()
        if (entries!!.entries.isEmpty())
            return completedFuture(null)
        return completedFuture(entries!!.entries.first())
    }

    @Throws(IOException::class)
    override fun add(id: HashValue, message: String, changes: List<HashValue>): Future<Unit> {
        val entry = BranchLogEntry(nextRevId(), id, message)
        entry.changes.addAll(changes)
        add(entry)
        return completedFuture(Unit)
    }

    override fun add(entry: BranchLogEntry): Future<Unit> {
        try {
            lock()
            if (entries == null)
                entries = readLogs()
            entries!!.entries.add(0, entry)
            write()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            unlock()
        }
        return completedFuture(Unit)
    }

    @Throws(IOException::class)
    private fun write() {
        if (entries == null)
            return

        // append to log
        if (!logFile.exists()) {
            logFile.parentFile.mkdirs()
            logFile.createNewFile()
        }
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(logFile)))
        try {
            val out = JSON(indented = true).stringify(entries!!)
            writer.write(out)
        } finally {
            writer.close()
        }
    }

    companion object {
        private fun getLocalDir(logDir: File): File {
            return File(logDir, "local")
        }
    }
}
