package org.fejoa.network

import kotlinx.serialization.Serializable
import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream


@Serializable
class RPCPipeResponse(val message: String)

/**
 * Provides an OutputStream to send data and an InputStream to receive the reply.
 *
 * Once a request is sent by writing data to the output stream the previous input stream is closed.
 *
 * @param header is prepended to data written to the output stream.
 * @param remoteRequest the remote request to be used
 * @param onDataSentCallback is called when a request has been sent, i.e. when the user starts reading the reply
 */
class RemotePipeImpl(private val header: String, private val remoteRequest: RemoteRequest,
                     private val onDataSentCallback: (() -> Any)?) : RemotePipe {
    private val rawInStream = RemoteInStream()
    private val rawOutStream = RemoteOutStream()
    override val inStream: AsyncInStream = rawInStream
    override val outStream: AsyncOutStream = rawOutStream

    private inner class RemoteInStream : AsyncInStream {
        var reply: RemoteRequest.Reply? = null

        suspend override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (reply == null) {
                if (rawOutStream.dataSender == null)
                    throw Exception("No ongoing request")
                onDataSentCallback?.invoke()
                reply = rawOutStream.dataSender!!.send()
                rawOutStream.dataSender = null
            }
            return reply!!.inStream().read(buffer, offset, length)
        }
    }

    private inner class RemoteOutStream : AsyncOutStream {
        var dataSender: RemoteRequest.DataSender? = null

        suspend override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            if (rawInStream.reply != null) {
                rawInStream.reply!!.close()
                rawInStream.reply = null
            }

            if (dataSender == null)
                dataSender = remoteRequest.sendData(header)

            dataSender!!.outStream().write(buffer, offset, length)
            return length
        }

        suspend override fun flush() {
            dataSender?.outStream()?.flush()
        }

        suspend override fun close() {
            dataSender?.outStream()?.close()
            dataSender = null
        }
    }
}
