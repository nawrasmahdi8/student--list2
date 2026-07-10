package com.example.network

import android.content.Context
import com.example.ui.RegistryViewModel
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class P2PServer(
    port: Int,
    private val context: Context,
    private val viewModel: RegistryViewModel,
    private val mode: DataTransferMode,
    private val zipFile: File
) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        if (!zipFile.exists()) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Zip file not found")
        }
        val fis = FileInputStream(zipFile)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/zip",
            fis,
            zipFile.length()
        )
    }
}
