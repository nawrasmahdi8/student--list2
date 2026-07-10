package com.example.network

import android.content.Context
import com.example.ui.RegistryViewModel
import com.example.utils.DataArchiver
import fi.iki.elonen.NanoHTTPD

class P2PServer(port: Int, private val context: Context, private val viewModel: RegistryViewModel, private val mode: DataTransferMode) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val file = DataArchiver.zipData(context, mode, viewModel)
        return newChunkedResponse(Response.Status.OK, "application/zip", file.inputStream())
    }
}
