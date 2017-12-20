package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import org.fejoa.support.*


class PingJob : RemoteJob<PingJob.Result>() {
    companion object {
        val METHOD = "ping"
    }

    class Result(status: ReturnType, message: String, val headerResponse: String, val dataResponse: String)
        : RemoteJob.Result(status, message)

    @Serializable
    class PingParam(val text: String)

    private fun getHeader(): String {
        return JsonRPCRequest(id = id, method = METHOD, params = PingParam("ping"))
                .stringify(PingParam.serializer())
    }

    suspend override fun run(remoteRequest: RemoteRequest): Result {
        val sender = remoteRequest.sendData(getHeader())
        sender.outStream().write("PING".toUTF())
        val reply = sender.send()
        val responseHeader = reply.receiveHeader()
        val response = try {
            JsonRPCResult.parse(StringSerializer, responseHeader, id)
        } catch (e: Exception) {
            val error = JsonRPCError.parse(ErrorMessage::class.serializer(), responseHeader, id).error
            return Result(ensureError(error.code), error.message, "", "")
        }

        val inStream = reply.inStream()
        val dataResponse = inStream.readAll().toUTFString()
        val message = "Header: " + response.result + " Data: " + dataResponse
        return Result(ReturnType.OK, message, response.result, dataResponse)
    }
}
