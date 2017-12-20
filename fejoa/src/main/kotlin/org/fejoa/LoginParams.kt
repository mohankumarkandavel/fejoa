package org.fejoa

import kotlinx.serialization.Serializable
import org.fejoa.crypto.DH_GROUP
import org.fejoa.crypto.UserKeyParams
import org.fejoa.support.decodeBase64
import org.fejoa.support.encodeBase64


val COMPACT_PAKE = "CompactPAKE"

/**
 * @param sharedSecret the P_{\pi} value
 * @param group used to calculate the sharedSecret P_pi
 */
@Serializable
data class LoginParams(val userKeyParams: UserKeyParams, val sharedSecret: String, val group: DH_GROUP,
                       val type: String = COMPACT_PAKE) {
    constructor(userKeyParams: UserKeyParams, sharedSecret: ByteArray, group: DH_GROUP, type: String = COMPACT_PAKE)
            : this(userKeyParams, sharedSecret.encodeBase64(), group, type)

    fun getSharedSecret(): ByteArray {
        return sharedSecret.decodeBase64()
    }
}
