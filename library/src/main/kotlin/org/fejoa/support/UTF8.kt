package org.fejoa.support

import kotlinx.serialization.stringFromUtf8Bytes
import kotlinx.serialization.toUtf8Bytes


fun String.toUTF(): ByteArray {
    return this.toUtf8Bytes()
}

fun ByteArray.toUTFString(): String {
    return stringFromUtf8Bytes(this)
}

/**
 * @return a normalized path
 */
fun String.toUTFPath(): ByteArray {
    // TODO: implement
    return this.toUTF()
}


/**
 * @return a normalized path
 */
fun ByteArray.toUTFPath(): String {
    // TODO: implement
    return this.toUTFString()
}
