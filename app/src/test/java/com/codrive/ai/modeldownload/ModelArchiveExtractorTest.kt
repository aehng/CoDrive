package com.codrive.ai.modeldownload

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArchiveExtractorTest {
    @Test
    fun extractTarBz2_writesFiles() {
        val root = Files.createTempDirectory("archive-test").toFile()
        val archive = File(root, "test.tar.bz2")
        val outputDir = File(root, "out")

        val tarBytes = ByteArrayOutputStream().use { byteStream ->
            BZip2CompressorOutputStream(byteStream).use { bz2 ->
                TarArchiveOutputStream(bz2).use { tar ->
                    val entry = TarArchiveEntry("sample.txt")
                    val data = "hello".toByteArray()
                    entry.size = data.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(data)
                    tar.closeArchiveEntry()
                }
            }
            byteStream.toByteArray()
        }
        archive.writeBytes(tarBytes)

        ModelArchiveExtractor.extractTarBz2(archive, outputDir)

        assertTrue(File(outputDir, "sample.txt").exists())
    }
}

