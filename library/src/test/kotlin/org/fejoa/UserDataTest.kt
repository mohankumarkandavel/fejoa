package org.fejoa

import kotlinx.coroutines.experimental.runBlocking
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
        val namespace = "testCreateOpen"
        cleanUp += namespace
        val context = FejoaContext(namespace, NowExecutor())
        val userData = UserData.create(context)
        userData.commit()

        val loadedUserData = UserData.open(context, userData.keyStore.branch,
                userData.keyStore.masterCredentials, userData.branch)

        assertEquals(userData.branch, loadedUserData.branch)
    }
}
