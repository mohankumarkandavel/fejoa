package org.fejoa.network

import kotlinx.serialization.internal.IntSerializer
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

        // create a response for the client
        val response = parsedRequest.makeResponse(5).stringify(IntSerializer)

        // at client:
        var failed = false
        try {
            val wrongId = id + 1
            JsonRPCResult.parse(ErrorMessage::class.serializer(), response, wrongId)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)

        val parsedResponse = JsonRPCResult.parse(IntSerializer, response, id)
        assertEquals(5, parsedResponse.result)
    }
}
