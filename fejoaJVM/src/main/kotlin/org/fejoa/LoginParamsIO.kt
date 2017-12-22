package org.fejoa

import kotlinx.serialization.json.JSON
import org.fejoa.support.PathUtils
import java.io.*



actual fun platformGetAccountIO(type: AccountIO.Type, context: String, namespace: String): AccountIO {
    return JVMAccountIO(context, namespace)
}


class JVMAccountIO(val context: String, val namespace: String) : AccountIO {
    private fun accountDir(path: String, namespace: String): File {
        return File(path, namespace)
    }

    private fun authDataFile(accountDir: File): File {
        return File(accountDir, "AuthData.json")
    }

    private fun userDataSettingsFile(accountDir: File): File {
        return File(accountDir, "UserDataConfig.json")
    }

    suspend override fun exists(): Boolean {
        val dir = accountDir(context, namespace)
        if (!dir.exists())
            return false

        if (authDataFile(dir).exists())
            return true
        if (userDataSettingsFile(dir).exists())
            return true

        return false
    }

    suspend override fun writeLoginData(loginData: LoginParams) {
        val dir = accountDir(context, namespace)
        dir.mkdirs()
        val fileName = authDataFile(dir)
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(fileName)))
        writer.write(JSON(indented = true).stringify(loginData))
        writer.close()
    }

    suspend override fun readLoginData(): LoginParams {
        val fileName = authDataFile(accountDir(context, namespace))
        val input = BufferedReader(InputStreamReader(FileInputStream(fileName)))
        val json = input.readText()
        input.close()
        return JSON.parse(json)
    }


    suspend override fun writeUserDataConfig(userDataConfig: UserDataConfig) {
        val dir = accountDir(context, namespace)
        dir.mkdirs()
        val fileName = userDataSettingsFile(dir)
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(fileName)))
        writer.write(JSON(indented = true).stringify(userDataConfig))
        writer.close()
    }

    suspend override fun readUserDataConfig(): UserDataConfig {
        val dir = accountDir(context, namespace)
        val fileName = userDataSettingsFile(dir)
        val input = BufferedReader(InputStreamReader(FileInputStream(fileName)))
        val json = input.readText()
        input.close()
        return JSON.parse(json)
    }
}
