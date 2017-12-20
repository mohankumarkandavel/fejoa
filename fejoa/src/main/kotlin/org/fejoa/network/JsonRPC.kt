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

    fun makeError(error: ReturnType, message: String): String {
        return JsonRPCMethodRequest.makeError(id, ErrorMessage(ensureError(error), message))
    }

    fun <T>makeResponse(result: T): JsonRPCResult<T> {
        return JsonRPCResult(id, result)
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

        fun makeError(id: Int, error: ReturnType, message: String): String {
            return makeError(id, ErrorMessage(ensureError(error), message))
        }

        fun makeError(id: Int, error: ErrorMessage): String {
            return JsonRPCError(id = id, error = error).stringify(ErrorMessage::class.serializer())
        }
    }

    fun makeError(error: ReturnType, message: String): String {
        return makeError(id, ErrorMessage(ensureError(error), message))
    }
}


@Serializable
class JsonRPCResult<T>(val id: Int, val result: T, val jsonrpc: String = RPC_VERSION) {
    companion object {
        fun <T>parse(serializer: KSerializer<T>, json: String, expectedId: Int): JsonRPCResult<T> {
            val response = JSON.parse(JsonRPCResult.serializer(serializer), json)
            if (response.id != expectedId)
                throw Exception("Id miss match. Expected: $expectedId, Actual: ${response.id}")
            return response
        }
    }

    fun stringify(serializer: KSerializer<T>): String {
        return JSON.stringify(JsonRPCResult.serializer(serializer), this)
    }
}

@Serializable
class ErrorMessage(val code: ReturnType, val message: String)


@Serializable
class JsonRPCError<T>(val id: Int?, val error: T, val jsonrpc: String = RPC_VERSION) {
    companion object {
        fun <T>parse(serializer: KSerializer<T>, json: String, expectedId: Int): JsonRPCError<T> {
            val response = JSON.parse(JsonRPCError.serializer(serializer), json)
            if (response.id != expectedId)
                throw Exception("Id miss match. Expected: $expectedId, Actual: ${response.id}")
            return response
        }
    }

    fun stringify(serializer: KSerializer<T>): String {
        return JSON.stringify(JsonRPCError.serializer(serializer), this)
    }
}
