package org.fejoa.crypto


class CryptoSettings private constructor() {

    var masterPassword = Password()
    var publicKey = Asymmetric()
    var signature = Signature()
    var symmetric = Symmetric()

    open class KeyTypeSettings {
        var keySize = -1
        var keyType: String = ""
    }

    class Password {
        // kdf
        var kdfAlgorithm: String = ""
        var kdfIterations = -1
        var passwordSize = -1
    }

    class Symmetric : KeyTypeSettings() {
        var algorithm: String = ""
        var ivSize = -1
    }

    class Asymmetric : KeyTypeSettings() {
        var algorithm: String = ""
    }

    class Signature : KeyTypeSettings() {
        var algorithm: String = ""
    }

    companion object {
        val SHA2 = "SHA2"
        val SHA3_256 = "SHA3_256"

        fun setDefaultEC(cryptoSettings: CryptoSettings) {
            cryptoSettings.publicKey.algorithm = "ECIES"
            cryptoSettings.publicKey.keyType = "ECIES/secp256r1"
            cryptoSettings.publicKey.keySize = 0

            cryptoSettings.signature.algorithm = "SHA256withECDSA"
            cryptoSettings.signature.keyType = "ECIES/secp256r1"
            cryptoSettings.signature.keySize = 0
        }

        fun setDefaultRSA(cryptoSettings: CryptoSettings) {
            cryptoSettings.publicKey.algorithm = "RSA/NONE/PKCS1PADDING"
            cryptoSettings.publicKey.keyType = "RSA"
            cryptoSettings.publicKey.keySize = 2048

            cryptoSettings.signature.algorithm = "SHA1withRSA"
            cryptoSettings.signature.keyType = "RSA"
            cryptoSettings.signature.keySize = 2048
        }

        val default: CryptoSettings
            get() {
                val cryptoSettings = CryptoSettings()

                setDefaultRSA(cryptoSettings)

                cryptoSettings.symmetric.algorithm = "AES/CTR/NoPadding"
                cryptoSettings.symmetric.keyType = "AES"
                cryptoSettings.symmetric.keySize = 256
                cryptoSettings.symmetric.ivSize = 16 * 8

                cryptoSettings.masterPassword.kdfAlgorithm = "PBKDF2WithHmacSHA256"
                cryptoSettings.masterPassword.kdfIterations = 20000
                cryptoSettings.masterPassword.passwordSize = 256

                return cryptoSettings
            }

        val fast: CryptoSettings
            get() {
                val cryptoSettings = default
                cryptoSettings.publicKey.keySize = 512

                cryptoSettings.symmetric.keySize = 128
                cryptoSettings.symmetric.ivSize = 16 * 8

                cryptoSettings.masterPassword.kdfIterations = 1

                return cryptoSettings
            }

        fun empty(): CryptoSettings {
            return CryptoSettings()
        }

        fun signatureSettings(algorithm: String): Signature {
            val cryptoSettings = signatureSettings()
            cryptoSettings.algorithm = algorithm
            return cryptoSettings
        }

        fun signatureSettings(): Signature {
            val settings = empty()
            val defaultSettings = default

            settings.signature.algorithm = defaultSettings.signature.algorithm
            settings.signature.keyType = defaultSettings.signature.keyType
            settings.signature.keySize = defaultSettings.signature.keySize
            return settings.signature
        }

        fun symmetricSettings(keyType: String, algorithm: String): Symmetric {
            val cryptoSettings = empty()
            cryptoSettings.symmetric.keyType = keyType
            cryptoSettings.symmetric.algorithm = algorithm
            return cryptoSettings.symmetric
        }

        fun symmetricKeyTypeSettings(keyType: String): CryptoSettings {
            val cryptoSettings = empty()
            cryptoSettings.symmetric.keyType = keyType
            return cryptoSettings
        }
    }
}
