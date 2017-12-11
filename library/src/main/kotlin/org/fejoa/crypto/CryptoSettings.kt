package org.fejoa.crypto


class CryptoSettings private constructor() {
    enum class SIGN_ALGO(val javaName: String, val jsName: String) {
        SHA256withECDSA("SHA256withECDSA", ""),
        SHA1withRSA("SHA1withRSA", "")
    }

    enum class ASYM_ALGO(val javaName: String, val jsName: String) {
        ECIES("ECIES", ""),
        RSA_NONE_PKCS1PADDING("RSA/NONE/PKCS1PADDING", "")
    }

    enum class SYM_ALGO(val javaName: String, val jsName: String) {
        AES_CTR("AES/CTR/NoPadding", "AES-CTR")
    }

    enum class KEY_TYPE(val javaName: String, val jsName: String) {
        AES("AES", "AES"),
        ECIES_secp256r1("ECIES/secp256r1", ""),
        RSA("RSA", "")
    }

    enum class HASH_TYPE(val jsName: String) {
        SHA256("SHA-256")
    }

    enum class KDF_ALGO(val javaName: String, val jsName: String, val hash: HASH_TYPE) {
        PBKDF2("PBKDF2WithHmacSHA256", "PBKDF2", HASH_TYPE.SHA256)
    }

    var masterPassword = Password()
    var publicKey = Asymmetric()
    var signature = Signature()
    var symmetric = Symmetric()

    open class KeyTypeSettings {
        var keySize = -1
        var keyType: KEY_TYPE = KEY_TYPE.AES
    }

    class Password {
        // kdf
        var kdfAlgorithm: KDF_ALGO = KDF_ALGO.PBKDF2
        var kdfIterations = -1
        var passwordSize = -1
    }

    class Symmetric : KeyTypeSettings() {
        var algorithm: SYM_ALGO = SYM_ALGO.AES_CTR
        var ivSize = -1
    }

    class Asymmetric : KeyTypeSettings() {
        var algorithm: ASYM_ALGO = ASYM_ALGO.RSA_NONE_PKCS1PADDING
    }

    class Signature : KeyTypeSettings() {
        var algorithm: SIGN_ALGO = SIGN_ALGO.SHA1withRSA
    }

    companion object {

        fun setDefaultEC(cryptoSettings: CryptoSettings) {
            cryptoSettings.publicKey.algorithm = ASYM_ALGO.ECIES
            cryptoSettings.publicKey.keyType = KEY_TYPE.ECIES_secp256r1
            cryptoSettings.publicKey.keySize = 0

            cryptoSettings.signature.algorithm = SIGN_ALGO.SHA256withECDSA
            cryptoSettings.signature.keyType = KEY_TYPE.ECIES_secp256r1
            cryptoSettings.signature.keySize = 0
        }

        fun setDefaultRSA(cryptoSettings: CryptoSettings) {
            cryptoSettings.publicKey.algorithm = ASYM_ALGO.RSA_NONE_PKCS1PADDING
            cryptoSettings.publicKey.keyType = KEY_TYPE.RSA
            cryptoSettings.publicKey.keySize = 2048

            cryptoSettings.signature.algorithm = SIGN_ALGO.SHA1withRSA
            cryptoSettings.signature.keyType = KEY_TYPE.RSA
            cryptoSettings.signature.keySize = 2048
        }

        val default: CryptoSettings
            get() {
                val cryptoSettings = CryptoSettings()

                setDefaultRSA(cryptoSettings)

                cryptoSettings.symmetric.algorithm = SYM_ALGO.AES_CTR
                cryptoSettings.symmetric.keyType = KEY_TYPE.AES
                cryptoSettings.symmetric.keySize = 256
                cryptoSettings.symmetric.ivSize = 16 * 8

                cryptoSettings.masterPassword.kdfAlgorithm = KDF_ALGO.PBKDF2
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

        fun signatureSettings(algorithm: SIGN_ALGO): Signature {
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

        fun symmetricSettings(keyType: KEY_TYPE, algorithm: SYM_ALGO): Symmetric {
            val cryptoSettings = empty()
            cryptoSettings.symmetric.keyType = keyType
            cryptoSettings.symmetric.algorithm = algorithm
            return cryptoSettings.symmetric
        }

        fun symmetricKeyTypeSettings(keyType: KEY_TYPE): CryptoSettings {
            val cryptoSettings = empty()
            cryptoSettings.symmetric.keyType = keyType
            return cryptoSettings
        }
    }
}
