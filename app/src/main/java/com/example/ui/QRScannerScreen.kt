package com.example.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.RegistryViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun QRScannerScreen(viewModel: RegistryViewModel, onResult: (String) -> Unit) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        launcher.launch(android.Manifest.permission.CAMERA)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
        
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    CompoundBarcodeView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult) {
                                if (!isDownloading) {
                                    isDownloading = true
                                    viewModel.downloadAndMerge(context, result.text, { progress ->
                                        downloadProgress = progress
                                    }) { success, error ->
                                        isDownloading = false
                                        onResult(if (success) "تم التحميل بنجاح" else "خطأ: $error")
                                    }
                                }
                            }
                        })
                        resume()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Text("يرجى منح صلاحية الكاميرا للمسح", modifier = Modifier.padding(16.dp))
        }
    }
}
