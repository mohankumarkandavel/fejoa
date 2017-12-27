package org.fejoa.repository

import kotlinx.serialization.Serializable

import kotlinx.serialization.Optional
import org.fejoa.storage.Config
import org.fejoa.storage.HashValue
import org.fejoa.support.Future


@Serializable
class BranchLogEntry(val time: Long = 0L,
                     var entryId: HashValue = Config.newDataHash(),
                     var message: String = "",
                     @Optional
                     val changes: MutableList<HashValue> = ArrayList())

@Serializable
class BranchLogList(val entries: MutableList<BranchLogEntry> = ArrayList()) {
    fun add(entry: BranchLogEntry) {
        entries.add(entry)
    }

    fun add(time: Long, id: HashValue, message: String, changes: MutableList<HashValue>) {
        entries.add(BranchLogEntry(time, id, message, changes))
    }
}


interface BranchLog {
    fun getBranchName(): String
    fun add(id: HashValue, message: String, changes: List<HashValue>): Future<Unit>
    fun add(entry: BranchLogEntry): Future<Unit>
    fun getEntries(): Future<List<BranchLogEntry>>
    fun getHead(): Future<BranchLogEntry?>
}