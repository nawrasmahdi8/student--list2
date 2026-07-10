package com.example.utils

import android.content.Context
import com.example.network.DataTransferMode
import com.example.ui.RegistryViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DataArchiver {
    private val checksums = mutableListOf<String>()

    fun zipData(context: Context, mode: DataTransferMode, viewModel: RegistryViewModel): File {
        val zipFile = File(context.cacheDir, "transfer_data.zip")
        checksums.clear()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            when (mode) {
                DataTransferMode.DATA_ONLY -> {
                    val jsonFile = viewModel.exportToJSON(context)
                    if (jsonFile != null) addFileToZip(zos, jsonFile, "backup.json")
                }
                DataTransferMode.IMAGES_ONLY -> {
                    val imagesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                    if (imagesDir != null) addDirectoryToZip(zos, imagesDir, "images")
                }
                DataTransferMode.FULL_ARCHIVE -> {
                    val jsonFile = viewModel.exportToJSON(context)
                    if (jsonFile != null) addFileToZip(zos, jsonFile, "backup.json")
                    val imagesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                    if (imagesDir != null) addDirectoryToZip(zos, imagesDir, "images")
                }
            }
            // Add metadata
            val metadata = "{\"version\": 2, \"timestamp\": ${System.currentTimeMillis()}}"
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(metadata.toByteArray())
            zos.closeEntry()

            // Add checksums
            val checksumContent = checksums.joinToString("\n")
            zos.putNextEntry(ZipEntry("checksum.txt"))
            zos.write(checksumContent.toByteArray())
            zos.closeEntry()
        }
        return zipFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, fileName: String) {
        if (file.exists()) {
            zos.putNextEntry(ZipEntry(fileName))
            val md = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                    md.update(buffer, 0, read)
                }
            }
            zos.closeEntry()
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            checksums.add("$hash $fileName")
        }
    }

    private fun addDirectoryToZip(zos: ZipOutputStream, directory: File, baseName: String) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addDirectoryToZip(zos, file, "$baseName/${file.name}")
            } else {
                addFileToZip(zos, file, "$baseName/${file.name}")
            }
        }
    }
}
