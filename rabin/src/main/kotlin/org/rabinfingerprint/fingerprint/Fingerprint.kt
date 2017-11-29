package org.rabinfingerprint.fingerprint


/**
 * Overview of Rabin's scheme given by Broder
 *
 * Some Applications of Rabin's Fingerprinting Method
 * http://citeseer.ist.psu.edu/cache/papers/cs/752/ftp:zSzzSzftp.digital.comzSzpubzSzDECzSzSRCzSzpublicationszSzbroderzSzfing-appl.pdf/broder93some.pdf
 */
interface Fingerprint<T> {
    fun pushBytes(bytes: ByteArray)
    fun pushBytes(bytes: ByteArray, offset: Int, length: Int)
    fun pushByte(b: Byte)
    fun reset()

    val fingerprint: T

    interface WindowedFingerprint<T> : Fingerprint<T> {
        fun popByte()
    }
}
