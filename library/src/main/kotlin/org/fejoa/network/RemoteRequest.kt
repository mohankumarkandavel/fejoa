package org.fejoa.network

import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream


interface RemoteRequest {
    interface DataSender {
        fun outStream(): AsyncOutStream
        fun send(): Reply
        fun close()
    }

    interface Reply {
        suspend fun receiveHeader(): String
        suspend fun inStream(): AsyncInStream

        fun close()
    }

    fun send(header: String): Reply
    fun sendData(header: String): DataSender
}

object HTMLRequestMultipartKeys {
    val MESSAGE_KEY = "header"
    val DATA_KEY = "data"
    val DATA_FILE = "binary.data"
}

expect fun platformCreateHTMLRequest(url: String): RemoteRequest
