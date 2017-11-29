package org.fejoa.jsbindings


@JsName("hex")
external fun hexJs(buffer: ByteArray): String

external fun hexToBytes(hex: String): Array<Byte>
external fun bytesToHex(bytes: Array<Byte>): String