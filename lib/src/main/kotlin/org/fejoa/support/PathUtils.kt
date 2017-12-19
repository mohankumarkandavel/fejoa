package org.fejoa.support

object PathUtils {
    fun appendDir(baseDir: String, dir: String): String {
        var newDir = baseDir
        if (dir == "")
            return baseDir
        if (newDir != "")
            newDir += "/"
        newDir += dir
        return newDir
    }

    fun fileName(path: String): String {
        val lastSlash = path.lastIndexOf("/")
        return if (lastSlash < 0) path else path.substring(lastSlash + 1)
    }

    fun dirName(path: String): String {
        val lastSlash = path.lastIndexOf("/")
        return if (lastSlash < 0) "" else path.substring(0, lastSlash)
    }
}