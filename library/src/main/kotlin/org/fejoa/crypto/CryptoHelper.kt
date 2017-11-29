package org.fejoa.crypto

import org.fejoa.support.toHex
import org.fejoa.support.toUTF


class CryptoHelper {
    companion object {
        fun sha256Hash(): AsyncHashOutStream {
            return getInstanceHashOutStream("SHA-256")
        }

        suspend fun sha256HashHex(data: ByteArray): String {
            return sha256Hash(data).toHex()
        }

        suspend fun sha256HashHex(data: String): String {
            return sha256HashHex(data.toUTF())
        }

        suspend fun hash(data: ByteArray, hashOutStream: AsyncHashOutStream): ByteArray {
            hashOutStream.reset()
            hashOutStream.write(data)
            return hashOutStream.hash()
        }

        suspend fun hash(data: ByteArray, offset: Int, length: Int, hashOutStream: AsyncHashOutStream): ByteArray {
            hashOutStream.reset()
            hashOutStream.write(data, offset, length)
            return hashOutStream.hash()
        }

        suspend fun sha256Hash(data: ByteArray): ByteArray {
            return hash(data, sha256Hash())
        }

        fun sha1Hash(): AsyncHashOutStream {
            return getInstanceHashOutStream("SHA-1")
        }

        suspend fun sha1HashHex(data: ByteArray): String {
            return hash(data, sha1Hash()).toHex()
        }

        suspend fun generateSha1Id(crypto: CryptoInterface): String {
            return sha1HashHex(crypto.generateSalt())
        }

        /*
        fun toJson(secretKey: SecretKey): JSONObject {
            val `object` = JSONObject()
            `object`.put(Constants.ALGORITHM_KEY, secretKey.keyAlgorithm())
            `object`.put(Constants.SECRET_KEY_KEY, toBase64(secretKey.getEncoded()))
            return `object`
        }

        fun secretKey(`object`: JSONObject): SecretKey {
            val algorithm = `object`.getString(Constants.ALGORITHM_KEY)
            val rawKey = fromBase64(`object`.getString(Constants.SECRET_KEY_KEY))
            return secretKey(rawKey, algorithm)
        }*/
    }
}
