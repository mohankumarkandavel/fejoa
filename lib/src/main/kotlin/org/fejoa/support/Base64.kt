package org.fejoa.support

expect fun encodeBase64String(data: ByteArray): String
expect fun decodeBase64(string: String): ByteArray
