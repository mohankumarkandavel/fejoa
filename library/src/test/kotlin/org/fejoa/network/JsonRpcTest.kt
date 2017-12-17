package org.fejoa.network

import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class JsonRpcTest {
    @Test
    fun testBasics() {
        val id = 2
        // at client:
        val request = JsonRPCRequest(id = id, method = "method", params = "test")
        val requestJson = request.stringify(StringSerializer)

        // at server:
        // parse basics
        val basicRequest = JsonRPCMethodRequest.parse(requestJson)
        assertEquals("method", basicRequest.method)
        // parse the full request
        val parsedRequest = JsonRPCRequest.parse(StringSerializer, requestJson)
        assertEquals("test", parsedRequest.params)

        // make response for client
        val response = parsedRequest.makeResponse(0, "FromServer")

        // at client:
        var failed = false
        try {
            val wrongId = id + 1
            JsonRPCResponse.parse(JsonRPCStatusResult::class.serializer(), response, wrongId)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)

        val parsedResponse = JsonRPCResponse.parse(JsonRPCStatusResult::class.serializer(), response, id)
        assertEquals("FromServer", parsedResponse.result.message)
    }
}
