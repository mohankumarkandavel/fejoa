package org.fejoa.server

import org.fejoa.network.Errors
import org.fejoa.network.PingJob
import org.json.JSONException

import java.io.*


class JsonPingHandler : JsonRequestHandler(PingJob.METHOD) {
    override fun handle(responseHandler: Portal.ResponseHandler, jsonRPCHandler: JsonRPCHandler, data: InputStream?,
                        session: Session) {
        if (data == null)
            throw IOException("data expected!")

        val text: String
        try {
            text = jsonRPCHandler.params!!.getString("text")
        } catch (e: JSONException) {
            throw IOException("missing argument")
        }

        val response = jsonRPCHandler.makeResult(Errors.OK, text + " pong")
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
