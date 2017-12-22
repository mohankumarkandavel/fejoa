package org.fejoa

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.crypto.*
import org.fejoa.storage.platformCreateStorage
import org.fejoa.support.NowExecutor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals


class UserDataTest {
    val cleanUp: MutableList<String> = ArrayList()

    @AfterTest
    fun cleanUp() = runBlocking {
        val backend = platformCreateStorage()
        for (namespace in cleanUp)
            backend.deleteNamespace(namespace)
    }

    @Test
    fun testCreateOpen() = runBlocking {
        val contextPath = ""
        val namespace = "testCreateOpen"
        val password = "Password"
        cleanUp += namespace
        val context = FejoaContext(namespace, NowExecutor())
        val userData = UserData.create(context)
        userData.commit()

        val settings = userData.getUserDataSettings(password,
                UserKeyParams(BaseKeyParams(CryptoSettings.default.kdf, CryptoHelper.crypto.generateSalt16()),
                        CryptoSettings.HASH_TYPE.SHA256,
                        CryptoSettings.KEY_TYPE.AES, CryptoHelper.crypto.generateSalt16()))
        platformWriteUserDataConfig(contextPath, namespace, settings)

        val loadedSettings = platformReadUserDataConfig(contextPath, namespace)

        val newContext = FejoaContext(namespace, NowExecutor())
        val plainUserDataSettings = loadedSettings.open(password, newContext.baseKeyCache)
        val loadedUserData = UserData.open(newContext, plainUserDataSettings.first,
                plainUserDataSettings.second.branch)

        assertEquals(userData.branch, loadedUserData.branch)
    }
}
