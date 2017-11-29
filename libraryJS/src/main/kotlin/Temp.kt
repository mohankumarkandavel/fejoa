import org.khronos.webgl.ArrayBuffer

fun ArrayBuffer.asByteArray(): ByteArray {
    val arrayBuffer = this
    return js("Int8Array(arrayBuffer)")
}

fun temp() {
    val array = intArrayOf(1, 1, 2)
    val byteArray = ByteArray(10)
    val arrayBuffer = ArrayBuffer(10)
    val typedArray = byteArray.toTypedArray()
}