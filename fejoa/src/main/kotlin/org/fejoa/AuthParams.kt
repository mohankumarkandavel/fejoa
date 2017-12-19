package org.fejoa

import kotlinx.serialization.Serializable
import org.fejoa.crypto.DH_GROUP
import org.fejoa.crypto.UserKeyParams


/**
 * @param P_pi the P_{\pi} value
 * @param group used to calculate P_pi
 */
@Serializable
data class AuthParams(val userKeyParams: UserKeyParams, val P_pi: String, val group: DH_GROUP, val type: String = "CompactPAKE")


expect fun platformWriteAuthData(path: String, namespace: String, authData: AuthParams)
expect fun platformReadAuthData(path: String, namespace: String): AuthParams