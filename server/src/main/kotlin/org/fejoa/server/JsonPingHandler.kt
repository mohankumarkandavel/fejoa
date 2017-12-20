package org.fejoa.server

import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.network.JsonRPCRequest
import org.fejoa.network.PingJob

import java.io.*


class JsonPingHandler : JsonRequestHandler(PingJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, json: String, data: InputStream?,
                        session: Session) {
        if (data == null)
            throw IOException("data expected!")

        val request = JsonRPCRequest.parse(PingJob.PingParam::class.serializer(), json)
        val text = request.params.text

        val response = request.makeResponse(text + " pong").stringify(StringSerializer)
        responseHandler.setResponseHeader(response)

        val bufferedReader = BufferedReader(InputStreamReader(data))
        try {
            val dataLine = bufferedReader.readLine()
            val outputStream = responseHandler.addData()
            val writer = OutputStreamWriter(outputStream!!)
            writer.write(dataLine + " PONG")
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
            throw IOException("IO error: " + e.message)
        }
    }
}
