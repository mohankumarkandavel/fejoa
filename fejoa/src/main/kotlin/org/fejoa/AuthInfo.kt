package org.fejoa

import kotlinx.serialization.Serializable


enum class AuthType {
    PLAIN,
    LOGIN,
    //TOKEN
}

interface AuthInfo {
    val type: AuthType
}

@Serializable
class PlainAuthInfo(override val type: AuthType = AuthType.PLAIN) : AuthInfo


@Serializable
class LoginAuthInfo(override val type: AuthType = AuthType.LOGIN) : AuthInfo