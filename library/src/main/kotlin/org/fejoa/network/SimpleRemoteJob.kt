package org.fejoa.network

import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream


abstract class SimpleRemoteJob<T : RemoteJob.Result>(val hasData: Boolean) : RemoteJob<T>() {
    companion object {
        private var id: Int = -1
        fun nextId(): Int {
            id++
            return id
        }
    }

    val id: Int

    init {
        id = nextId()
    }

    abstract fun getHeader(): String
    abstract suspend fun handle(responseHeader: String, inStream: AsyncInStream): T

    suspend open fun writeData(outStream: AsyncOutStream) {

    }

    override suspend fun run(remoteRequest: RemoteRequest): T {
        val reply = if (hasData) {
            val sender = remoteRequest.sendData(getHeader())
            writeData(sender.outStream())
            sender.send()
        } else
            remoteRequest.send(getHeader())

        return handle(reply.receiveHeader(), reply.inStream())
    }
}
