package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import org.fejoa.support.*


class PingJob : SimpleRemoteJob<PingJob.Result>(true) {
    companion object {
        val METHOD = "ping"
    }

    class Result(status: Int, message: String, val headerResponse: String, val dataResponse: String)
        : RemoteJob.Result(status, message)

    @Serializable
    class PingParam(val text: String)

    override fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = PingParam("ping"))
                .stringify(PingParam.serializer())
    }

    suspend override fun writeData(outStream: AsyncOutStream) {
        outStream.write("PING".toUTF())
    }

    suspend override fun handle(responseHeader: String, inStream: AsyncInStream): Result {
        val response = JsonRPCResponse.parse<JsonRPCStatusResult>(JsonRPCStatusResult.serializer(),
                responseHeader, id)

        val params = response.result

        val dataResponse = inStream.readAll().toUTFString()
        val message = "Header: " + params.message + " Data: " + inStream.readAll().toUTFString()
        return Result(params.status, message, params.message, dataResponse)
    }
}
