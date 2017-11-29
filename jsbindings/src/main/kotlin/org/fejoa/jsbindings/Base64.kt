package org.fejoa.jsbindings

external object base64js {
    fun encode(buffer: ByteArray): String
    fun decode(str: String): ByteArray
}