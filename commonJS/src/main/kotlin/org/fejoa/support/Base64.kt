package org.fejoa.support

import org.fejoa.jsbindings.base64js


actual fun encodeBase64String(data: ByteArray): String {
    return base64js.encode(data)
}

actual fun decodeBase64(string: String): ByteArray {
    return base64js.decode(string)
}
