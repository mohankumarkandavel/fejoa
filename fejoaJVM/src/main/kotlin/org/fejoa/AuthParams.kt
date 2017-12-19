package org.fejoa

import kotlinx.serialization.json.JSON
import org.fejoa.support.PathUtils
import java.io.*


private fun authDataDir(path: String, namespace: String): String {
    return PathUtils.appendDir(path, namespace)
}

private fun authDataFile(path: String): String {
    return PathUtils.appendDir(path, "AuthData.json")
}

actual fun platformWriteAuthData(path: String, namespace: String, authData: AuthParams) {
    val dir = authDataDir(path, namespace)
    File(dir).mkdirs()
    val fileName = authDataFile(dir)
    val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(fileName)))
    writer.write(JSON(indented = true).stringify(authData))
    writer.close()
}

actual fun platformReadAuthData(path: String, namespace: String): AuthParams {
    val fileName = authDataFile(authDataDir(path, namespace))
    val input = BufferedReader(InputStreamReader(FileInputStream(fileName)))
    val json = input.readText()
    input.close()
    return JSON.parse(json)
}