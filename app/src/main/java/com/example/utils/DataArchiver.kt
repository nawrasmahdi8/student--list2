package com.example.utils

import android.content.Context
import com.example.network.DataTransferMode
import com.example.ui.RegistryViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DataArchiver {
    fun zipData(context: Context, mode: DataTransferMode, viewModel: RegistryViewModel): File {
        val zipFile = File(context.cacheDir, "transfer_data.zip")
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
        }
        return zipFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, fileName: String) {
        if (file.exists()) {
            zos.putNextEntry(ZipEntry(fileName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
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
