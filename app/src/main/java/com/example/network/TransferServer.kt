package com.example.network

import com.example.data.TransferScope
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class TransferServer(
    port: Int,
    private val scope: TransferScope,
    private val registryDataFile: File,
    private val imagesZipFile: File?
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        // Simple logic: return file based on requested scope
        // In a real implementation, you would handle scope logic more robustly
        
        val fileToServe = when (scope) {
            TransferScope.DATA_ONLY -> registryDataFile
            TransferScope.IMAGES_ONLY -> imagesZipFile ?: registryDataFile // Fallback
            TransferScope.FULL_ARCHIVE -> registryDataFile // Should be a combined zip
        }

        return try {
            val fis = FileInputStream(fileToServe)
            newFixedLengthResponse(
                Response.Status.OK,
                "application/octet-stream",
                fis,
                fileToServe.length()
            )
        } catch (e: IOException) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error serving file")
        }
    }
}
