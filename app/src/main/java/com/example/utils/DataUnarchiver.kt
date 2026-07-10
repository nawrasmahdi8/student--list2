package com.example.utils

import android.content.Context
import android.os.Environment
import com.example.ui.RegistryViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object DataUnarchiver {
    fun unzipAndMerge(context: Context, zipFile: File, viewModel: RegistryViewModel, onComplete: (Boolean, String?) -> Unit) {
        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        viewModel.importFromInputStream(zis) { imported, _, error ->
                            onComplete(error == null, error)
                        }
                    } else if (entry.name.startsWith("images/")) {
                        val fileName = entry.name.substringAfter("images/")
                        if (fileName.isNotEmpty()) {
                            val imagesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            val destFile = File(imagesDir, fileName)
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            onComplete(false, e.message)
        }
    }
}
