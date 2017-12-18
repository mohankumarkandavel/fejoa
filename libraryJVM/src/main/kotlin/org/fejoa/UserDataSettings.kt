package org.fejoa

import kotlinx.serialization.json.JSON
import org.fejoa.support.PathUtils
import java.io.*


private fun userDataSettingsFile(namespace: String): String {
    return PathUtils.appendDir(namespace, "UserDataSettings.json")
}

actual fun platformWriteUserDataSettings(namespace: String, userDataSettings: UserDataSettings) {
    val fileName = userDataSettingsFile(namespace)
    val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(fileName)))
    writer.write(JSON(indented = true).stringify(userDataSettings))
    writer.close()
}

actual fun platformReadUserDataSettings(namespace: String): UserDataSettings {
    val fileName = userDataSettingsFile(namespace)
    val input = BufferedReader(InputStreamReader(FileInputStream(fileName)))
    val json = input.readText()
    input.close()
    return JSON.parse(json)
}
