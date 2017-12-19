package org.fejoa.repository.sync

import kotlinx.serialization.SerialContext
import kotlinx.serialization.json.JSON
import org.fejoa.network.RemotePipe
import org.fejoa.support.StreamHelper
import org.fejoa.repository.BranchLogEntry
import org.fejoa.storage.HashValue
import org.fejoa.storage.HashValueDataSerializer


object LogEntryRequest {
    var MAX_HEADER_SIZE = 1024 * 32

    suspend fun getRemoteTip(remotePipe: RemotePipe, branch: String): BranchLogEntry? {
        val outputStream = remotePipe.outStream
        Request.writeRequestHeader(outputStream, Request.RequestType.GET_REMOTE_TIP)
        StreamHelper.writeString(outputStream, branch)

        val inputStream = remotePipe.inStream
        Request.receiveHeader(inputStream, Request.RequestType.GET_REMOTE_TIP)

        val header = StreamHelper.readString(inputStream, MAX_HEADER_SIZE)
        val context = SerialContext().apply {
            registerSerializer(HashValue::class, HashValueDataSerializer)
        }
        val head = JSON(context = context).parse<BranchLogEntry>(header)
        if (head.entryId.isZero)
            return null
        return head
    }
}
