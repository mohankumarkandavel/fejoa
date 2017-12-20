package org.fejoa


expect fun platformWriteLoginData(path: String, namespace: String, loginData: LoginParams)
expect fun platformReadLoginData(path: String, namespace: String): LoginParams
