package org.fejoa.support

fun ByteArray.toHex(): String {
    val alphabet = "0123456789abcdef"
    var result = ""
    for (i in this.indices) {
        val value = this[i].toInt()
        val first = (value shr 4) and 0x0F
        val second = value and 0x0F
        result += alphabet[first]
        result += alphabet[second]
    }
    return result
}

/**
 * Loosely based on:
 * http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
 */
fun fromHex(hexIn: String): ByteArray {
    val hex = if (hexIn.length.rem(2) == 0)
        hexIn
    else // try to fix the input string
        "0" + hexIn

    val length = hex.length
    val buffer = ByteArray(length / 2)
    var i = 0
    while (i < length) {
        buffer[i / 2] = ((hex.substring(i, i + 1).toInt(16) shl 4)
                + hex.substring(i + 1, i + 2).toInt(16)).toByte()
        i += 2
    }
    return buffer
}