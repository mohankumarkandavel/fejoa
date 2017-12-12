package org.fejoa.server

import kotlinx.serialization.json.JSON
import org.fejoa.network.JsonRPCSimpleResponse
import org.fejoa.network.JsonRPCStatusResult
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException


class JsonRPCHandler(jsonString: String) {
    val id: Int
    val method: String
    private val jsonObject: JSONObject = JSONObject(jsonString)
    var params: JSONObject? = null
        private set

    init {
        if (jsonObject.getString("jsonrpc") != "2.0")
            throw IOException("json rpc 2.0 expected")
        id = jsonObject.getInt("id")
        method = jsonObject.getString("method")

        try {
            params = jsonObject.getJSONObject("params")
        } catch (e: JSONException) {
        }

    }

    fun makeResult(result: JsonRPCStatusResult): String {
        return makeResult(id, result)
    }

    fun makeResult(error: Int, message: String): String {
        return makeResult(id, JsonRPCStatusResult(error, message))
    }

    companion object {
        fun makeResult(id: Int, error: Int, message: String): String {
            return makeResult(id, JsonRPCStatusResult(error, message))
        }

        fun makeResult(id: Int, result: JsonRPCStatusResult): String {
            return JSON.stringify(JsonRPCSimpleResponse(id = id, result = result))
        }
    }
}
