package org.fejoa.repository

import kotlinx.serialization.protobuf.ProtoBuf
import org.fejoa.crypto.*
import org.fejoa.protocolbufferlight.ProtocolBufferLight
import org.fejoa.storage.HashValue
import org.fejoa.support.await


class DefaultCommitSignature(val signer: HashValue, val signCredentials: SignCredentials,
                             val keyGetter: suspend (signer: HashValue, keyId: HashValue) -> PublicKey?)
    : CommitSignature {
    /**
     * @param message
     * @param hash the algorithm used to
     * @param signer the person how signed the commit
     * @param signingKey
     * @param signature
     */
    class SignedMessage(val message: ByteArray, val hashType: CryptoSettings.HASH_TYPE, val signer: HashValue,
                        val signingKey: HashValue, val signature: ByteArray, val settings: CryptoSettings.Signature) {

        companion object {
            val MESSAGE_KEY = 0
            val HASH_TYPE_KEY = 1
            val SIGNER_KEY = 2
            val SIGNING_KEY_KEY = 3
            val SIGNATURE_KEY = 4
            val SETTINGS_KEY = 5

            fun read(buffer: ProtocolBufferLight): SignedMessage {
                val message = buffer.getBytes(MESSAGE_KEY) ?: throw Exception("Missing element")
                val hashTypeRaw = buffer.getString(HASH_TYPE_KEY) ?: throw Exception("Missing element")
                val hashType = CryptoSettings.HASH_TYPE.valueOf(hashTypeRaw)
                val signer = buffer.getBytes(SIGNER_KEY) ?: throw Exception("Missing element")
                val signingKey = buffer.getBytes(SIGNING_KEY_KEY) ?: throw Exception("Missing element")
                val signature = buffer.getBytes(SIGNATURE_KEY) ?: throw Exception("Missing element")
                val settingsRaw = buffer.getBytes(SETTINGS_KEY) ?: throw Exception("Missing element")
                val settings = ProtoBuf.load<CryptoSettings.Signature>(settingsRaw)

                return SignedMessage(message, hashType, HashValue(signer), HashValue(signingKey), signature, settings)
            }
        }

        fun write(buffer: ProtocolBufferLight) {
            buffer.put(MESSAGE_KEY, message)
            buffer.put(HASH_TYPE_KEY, hashType.name)
            buffer.put(SIGNER_KEY, signer.bytes)
            buffer.put(SIGNING_KEY_KEY, signingKey.bytes)
            buffer.put(SIGNATURE_KEY, signature)
            buffer.put(SETTINGS_KEY, ProtoBuf.dump(settings))
        }
    }

    private suspend fun hash(message: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>,
                             hashType: CryptoSettings.HASH_TYPE): ByteArray {
        val hashStream = CryptoHelper.getHashStream(hashType)
        hashStream.write(message)
        hashStream.write(rootHashValue.bytes)
        for (parent in parents)
            hashStream.write(parent.bytes)
        return hashStream.hash()
    }

    override suspend fun signMessage(message: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): ByteArray {
        val hashType = CryptoSettings.HASH_TYPE.SHA256
        val hashValue = hash(message, rootHashValue, parents, hashType)

        val signature = CryptoHelper.crypto.sign(hashValue, signCredentials.keyPair.privateKey,
                signCredentials.settings).await()

        val signedMessage = SignedMessage(message, hashType, signer, signCredentials.keyPair.getId(), signature,
                signCredentials.settings)
        val buffer = ProtocolBufferLight()
        signedMessage.write(buffer)

        return buffer.toByteArray()
    }

    override suspend fun verifySignedMessage(signedMessage: ByteArray, rootHashValue: HashValue, parents: Collection<HashValue>): Boolean {
        val data = SignedMessage.read(ProtocolBufferLight(signedMessage))
        val hash = hash(data.message, rootHashValue, parents, data.hashType)
        val signerKey = keyGetter.invoke(data.signer, data.signingKey) ?: return false
        return CryptoHelper.crypto.verifySignature(hash, data.signature, signerKey, data.settings).await()
    }

}