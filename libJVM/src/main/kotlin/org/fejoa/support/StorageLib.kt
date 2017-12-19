package org.fejoa.support

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


/**
 * Collection of storage related operations.
 *
 * Note: these function may take a significant amount of time depending where the files are located. For example,
 * moving a file on the same storage is faster than moving it somewhere to a different storage because this would
 * involve a copy and a delete operation.
 *
 * TODO: add listener interfaces to monitor copy, move, delete... progress
 */
object StorageLib {
    /**
     * I file is a directory it deletes it recursively. If it is just a file it just deletes this file.
     * @param file
     * @return false on the first file that can't be deleted
     */
    fun recursiveDeleteFile(file: File): Boolean {
        if (file.isDirectory()) {
            val children = file.list()
            for (child in children!!) {
                if (!recursiveDeleteFile(File(file, child)))
                    return false
            }
        }
        return file.delete()
    }

    fun moveFile(source: File, destination: File): Boolean {
        if (source.isDirectory())
            return false
        // first try to just rename the file
        if (source.renameTo(destination))
            return true
        // this could have failed because the file is on different storage cards so to a hard copy and then delete it
        return if (!copyFile(source, destination)) false else source.delete()

    }

    /**
     * Copies a file (not a directory) from source to destination
     * @param source
     * @param destination
     * @return
     */
    fun copyFile(source: File, destination: File): Boolean {
        try {
            val inStream = FileInputStream(source)
            val outStream = FileOutputStream(destination)
            val inChannel = inStream.getChannel()
            inChannel.transferTo(0, inChannel.size(), outStream.getChannel())
            inStream.close()
            outStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        return true
    }

    fun copyDir(sourceDir: File, destinationDir: File): Boolean {
        destinationDir.mkdirs()
        val files = sourceDir.listFiles() ?: return true
        for (sub in files) {
            val ok: Boolean
            if (sub.isFile())
                ok = copyFile(sub, File(destinationDir, sub.getName()))
            else
                ok = copyDir(sub, File(destinationDir, sub.getName()))
            if (!ok)
                return false
        }
        return true
    }
}
