package org.fejoa.support

import org.apache.commons.codec.binary.Base64


actual fun encodeBase64String(data: ByteArray): String {
    return Base64.encodeBase64String(data)
}

actual fun decodeBase64(string: String): ByteArray {
    return Base64.decodeBase64(string)
}