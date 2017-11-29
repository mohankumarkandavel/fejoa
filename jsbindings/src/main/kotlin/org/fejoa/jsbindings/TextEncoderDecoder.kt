package org.fejoa.jsbindings

import kotlin.js.Json


external open class TextEncoder {
    constructor()

    fun encode(text: String, option: Json = definedExternally): ByteArray
}

external open class TextDecoder {
    constructor()

    fun decode(buffer: ByteArray, option: Json = definedExternally): String
}