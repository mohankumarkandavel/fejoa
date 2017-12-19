package org.fejoa.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import kotlinx.serialization.serializer

val RPC_VERSION = "2.0"

@Serializable
class JsonRPCSimpleRequest(val jsonrpc: String = RPC_VERSION, val id: Int, val method: String)

@Serializable
class JsonRPCRequest<T>(val jsonrpc: String = RPC_VERSION, val id: Int, val method: String, val params: T) {
    companion object {
        fun <T>parse(serializer: KSerializer<T>, json: String): JsonRPCRequest<T> {
            return JSON.parse(JsonRPCRequest.serializer(serializer), json)
        }
    }

    fun stringify(serializer: KSerializer<T>): String {
        return JSON.stringify(JsonRPCRequest.serializer(serializer), this)
    }

    fun makeResponse(error: Int, message: String): String {
        return JsonRPCMethodRequest.makeResponse(id, JsonRPCStatusResult(error, message))
    }
}


// only to parse the method name before a more detailed class can be parsed
@Serializable
class JsonRPCMethodRequest(val jsonrpc: String = RPC_VERSION, val id: Int, val method: String) {
    companion object {
        /**
         * Verifies the jsonrpc version and returns the method field
         */
        fun parse(jsonrpc: String): JsonRPCMethodRequest {
            val response = JSON(nonstrict = true).parse<JsonRPCMethodRequest>(jsonrpc)
            if (response.jsonrpc != RPC_VERSION)
                throw Exception("Invalid json rpc version: ${response.jsonrpc}")
            return response
        }

        fun makeResponse(id: Int, error: Int, message: String): String {
            return makeResponse(id, JsonRPCStatusResult(error, message))
        }

        fun makeResponse(id: Int, result: JsonRPCStatusResult): String {
            return JsonRPCResponse(id = id, result = result).stringify(JsonRPCStatusResult::class.serializer())
        }
    }

    fun makeResponse(error: Int, message: String): String {
        return makeResponse(id, JsonRPCStatusResult(error, message))
    }
}


@Serializable
class JsonRPCStatusResult(val status: Int, val message: String)

@Serializable
class JsonRPCResponse<T>(val jsonrpc: String = RPC_VERSION, val id: Int, val result: T) {
    companion object {
        fun <T>parse(serializer: KSerializer<T>, json: String, expectedId: Int): JsonRPCResponse<T> {
            val response = JSON.parse(JsonRPCResponse.serializer(serializer), json)
            if (response.id != expectedId)
                throw Exception("Id miss match. Expected: $expectedId, Actual: ${response.id}")
            return response
        }
    }

    fun stringify(serializer: KSerializer<T>): String {
        return JSON.stringify(JsonRPCResponse.serializer(serializer), this)
    }
}
