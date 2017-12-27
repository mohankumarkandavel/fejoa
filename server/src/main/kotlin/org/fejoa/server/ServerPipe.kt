package org.fejoa.server

import org.fejoa.network.RemotePipe
import org.fejoa.network.toAsyncInStream
import org.fejoa.network.toAsyncOutStream
import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream
import java.io.InputStream


class ServerPipe(responseHeader: String, responseHandler: Portal.ResponseHandler, inputStream: InputStream) : RemotePipe {
    private class ServerPipeOutStream(val responseHeader: String, val responseHandler: Portal.ResponseHandler)
        : AsyncOutStream {
        var rawOutputStream: AsyncOutStream? = null

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            val out = rawOutputStream ?: run {
                responseHandler.setResponseHeader(responseHeader)
                responseHandler.addData().toAsyncOutStream().also {
                    rawOutputStream = it
                }
            }
            return out.write(buffer, offset, length)
        }
    }

    override val outStream: AsyncOutStream = ServerPipeOutStream(responseHeader, responseHandler)
    override val inStream: AsyncInStream = inputStream.toAsyncInStream()
}
