package org.fejoa.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON

val RPC_VERSION = "2.0"

// only to parse the method name before a more detailed class can be parsed
@Serializable
private class JsonRPCMethodResponse(val jsonrpc: String = RPC_VERSION, val method: String)

/**
 * Verifies the jsonrpc version and returns the method field
 */
fun parseMethod(jsonrpc: String): String {
    val response = JSON(nonstrict = true).parse<JsonRPCMethodResponse>(jsonrpc)
    if (response.jsonrpc != RPC_VERSION)
        throw Exception("Invalid json rpc version: ${response.jsonrpc}")
    return response.method
}

@Serializable
open class JsonRPCSimpleRequest(val jsonrpc: String = RPC_VERSION, val id: Int, val method: String)

// TODO: Serializable doesn't support generics right now...
//@Serializable
//class JsonRPCRequest<T>(val jsonrpc: String = "2.0", val id: Int, val method: String, val params: T)

@Serializable
class JsonRPCStatusResult(val status: Int, val message: String)

@Serializable
class JsonRPCSimpleResponse(val jsonrpc: String = RPC_VERSION, val id: Int, val result: JsonRPCStatusResult)
