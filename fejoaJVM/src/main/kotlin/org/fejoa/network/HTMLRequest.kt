package org.fejoa.network

import org.eclipse.jetty.util.MultiPartInputStreamParser
import org.fejoa.crypto.CryptoHelper
import org.fejoa.support.AsyncInStream
import org.fejoa.support.AsyncOutStream
import org.fejoa.support.toHex
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import javax.servlet.ServletException


fun OutputStream.toAsyncOutStream(): AsyncOutStream {
    val that = this
    return object : AsyncOutStream {
        suspend override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
            that.write(buffer, offset, length)
            return length
        }

        suspend override fun close() {
            that.close()
        }
    }
}

fun InputStream.toAsyncInStream(): AsyncInStream {
    val that = this
    return object : AsyncInStream {
        suspend override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return that.read(buffer, offset, length)
        }

        suspend override fun close() {
            that.close()
        }
    }
}

class HTMLRequest(val url: String) : RemoteRequest {
    companion object {
        private val LINE_FEED = "\r\n"
    }

    private fun getBoundary(): String {
        val randomValue = CryptoHelper.crypto.generateSalt16().toHex()
        return "===" + System.currentTimeMillis() + "_" + randomValue + "==="
    }

    private class Reply(val header: String, val connectionInputStream: InputStream, val dataInStream: InputStream) : RemoteRequest.Reply {
        private var closed = false
        private val inStream = dataInStream.toAsyncInStream()

        suspend override fun receiveHeader(): String {
            return header
        }

        suspend override fun inStream(): AsyncInStream {
            return inStream
        }

        override fun close() {
            if (closed)
                return
            closed = true

            connectionInputStream.close()
            dataInStream.close()
        }

    }

    private class Sender(val connection: HttpURLConnection, val boundary: String, val outStream: AsyncOutStream,
                         val writer: PrintWriter) : RemoteRequest.DataSender {
        private var closed = false

        override fun outStream(): AsyncOutStream {
            return outStream
        }

        override fun send(): RemoteRequest.Reply {
            close()

            val inputStream = connection.getInputStream()

            var line = ""
            var character = inputStream.read()
            while (character >= 0 && character != '\n'.toInt()) {
                line += character.toChar()
                character = inputStream.read()
            }
            line = line.replace("Content-Type: ", "")
            val parser = MultiPartInputStreamParser(inputStream,
                    line, null, null)
            try {
                val messagePart = parser.getPart(HTMLRequestMultipartKeys.MESSAGE_KEY)
                val dataPart = parser.getPart(HTMLRequestMultipartKeys.DATA_KEY)

                val bufferedInputStream = BufferedInputStream(messagePart.getInputStream())
                val receivedData = ByteArrayOutputStream()
                bufferedInputStream.copyTo(receivedData)

                val receivedHeader = receivedData.toString()
                println("RECEIVED: " + receivedHeader)

                val dataInputStream = dataPart?.getInputStream() ?: ByteArrayInputStream(ByteArray(0))
                return Reply(receivedHeader, inputStream, dataInputStream)
            } catch (e: ServletException) {
                e.printStackTrace()
                throw IOException("Unexpected server response.")
            }
        }

        override fun close() {
            if (closed)
                return
            closed = true

            writer.append(LINE_FEED)
            writer.append("--$boundary--").append(LINE_FEED)
            writer.append(LINE_FEED).flush()
            writer.close()
        }

    }

    override fun send(header: String): RemoteRequest.Reply {
        return sendData(header, false).send()
    }

    override fun sendData(header: String): RemoteRequest.DataSender {
        return sendData(header, true)
    }

    private fun sendData(header: String, outgoingData: Boolean): RemoteRequest.DataSender {
        println("SEND:     " + header)

        val server = URL(url)

        val boundary = getBoundary()
        val connection = server.openConnection() as HttpURLConnection
        connection.setUseCaches(false)
        connection.setDoOutput(true)
        connection.setDoInput(true)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        connection.setRequestProperty("Accept-Charset", "utf-8")

        connection.connect()
        val outputStream = connection.getOutputStream()

        val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)
        // header
        writer.append("--" + boundary).append(LINE_FEED)
        writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED)
        writer.append("Content-Disposition: form-data; name=\"" + HTMLRequestMultipartKeys.MESSAGE_KEY + "\"").append(LINE_FEED)
        writer.append(LINE_FEED)
        writer.append(header)

        // body
        if (outgoingData) {
            writer.append(LINE_FEED)
            writer.append("--" + boundary).append(LINE_FEED)
            writer.append("Content-Type: \"application/octet-stream\"").append(LINE_FEED)
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED)
            writer.append("Content-Disposition: form-data; name=\"" + HTMLRequestMultipartKeys.DATA_KEY
                    + "\"; filename=\"" + HTMLRequestMultipartKeys.DATA_FILE + "\"").append(LINE_FEED)
            writer.append(LINE_FEED)
            writer.flush()
        }
        return Sender(connection, boundary, outputStream.toAsyncOutStream(), writer)
    }
}

actual fun platformCreateHTMLRequest(url: String): RemoteRequest {
    return HTMLRequest(url)
}
