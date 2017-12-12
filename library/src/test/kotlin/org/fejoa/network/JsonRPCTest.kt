package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import kotlin.test.Test
import kotlin.test.assertEquals



class JsonRPCTest {
    @Serializable
    class TestRequest(val jsonrpc: String = "2.0", val id: Int, val method: String, val params: String)

    @Test
    fun testBasics() {
        val request = TestRequest(id = 0, method = "method", params = "test")
        val requestJson = JSON.stringify(request)
        val method = parseMethod(requestJson)
        assertEquals("method", method)

        val parsedRequest = JSON.parse<TestRequest>(requestJson)

        assertEquals("test", parsedRequest.params)
    }
}