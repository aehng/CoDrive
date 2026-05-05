package com.codrive.ai.modeldownload

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

object ModelArchiveExtractor {
    @Throws(IOException::class)
    fun extractTarBz2(archiveFile: File, outputDir: File) {
        if (!archiveFile.exists()) {
            throw IOException("Archive not found: ${archiveFile.absolutePath}")
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        FileInputStream(archiveFile).use { fis ->
            BZip2CompressorInputStream(fis).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry: TarArchiveEntry?
                    while (true) {
                        entry = tar.nextTarEntry ?: break
                        val outPath = File(outputDir, entry.name)
                        if (entry.isDirectory) {
                            outPath.mkdirs()
                        } else {
                            outPath.parentFile?.mkdirs()
                            outPath.outputStream().use { output ->
                                tar.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
    }
}

