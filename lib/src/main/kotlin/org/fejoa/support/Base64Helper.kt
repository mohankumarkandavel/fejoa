package org.fejoa.support

fun ByteArray.encodeBase64(): String {
    return encodeBase64String(this)
}

fun String.decodeBase64(): ByteArray {
    return decodeBase64(this)
}
