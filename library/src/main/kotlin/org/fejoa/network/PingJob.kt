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
    class PingRequest(val id: Int, val method: String, val params: PingParam, val jsonrpc: String = RPC_VERSION)
    @Serializable
    class PingParam(val text: String)

    override fun getHeader(): String {
        return JSON.stringify(PingRequest(id = id, method = METHOD, params = PingParam("ping")))
    }

    suspend override fun writeData(outStream: AsyncOutStream) {
        outStream.write("PING".toUTF())
    }

    suspend override fun handle(responseHeader: String, inStream: AsyncInStream): Result {
        val response = JSON.parse<JsonRPCSimpleResponse>(responseHeader)
        if (response.id != id)
            throw Exception("Id miss match. Expected: $id, Actual: ${response.id}")
        val params = response.result

        val dataResponse = inStream.readAll().toUTFString()
        val message = "Header: " + params.message + " Data: " + inStream.readAll().toUTFString()
        return Result(params.status, message, params.message, dataResponse)
    }
}
