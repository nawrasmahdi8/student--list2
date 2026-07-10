package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.ConnectionData
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(viewModel: RegistryViewModel, onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var hasCameraPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var statusMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            statusMessage = "يرجى منح صلاحية الكاميرا للمسح"
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مسح باركود لاستلام البيانات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isDownloading) {
                Text(statusMessage, modifier = Modifier.padding(16.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            } else if (hasCameraPermission) {
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
                                        try {
                                            val connectionData = Json.decodeFromString<ConnectionData>(result.text)
                                            if (connectionData.version != 2) {
                                                statusMessage = "إصدار الباركود غير مدعوم"
                                                return
                                            }
                                            isDownloading = true
                                            statusMessage = "Connected. Downloading..."
                                            
                                            viewModel.downloadAndMerge(context, result.text, { progress ->
                                                downloadProgress = progress
                                            }) { success, error ->
                                                isDownloading = false
                                                if (success) {
                                                    statusMessage = "Completed"
                                                    onResult("تم التحميل بنجاح")
                                                    onBack()
                                                } else {
                                                    statusMessage = "خطأ: $error"
                                                    onResult("خطأ: $error")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("QRScanner", "Invalid QR JSON", e)
                                            statusMessage = "باركود غير صالح"
                                        }
                                    }
                                }
                            })
                            resume()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (statusMessage.isNotEmpty()) {
                    Text(statusMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else {
                Text(statusMessage, modifier = Modifier.padding(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("طلب الصلاحية")
                }
            }
        }
    }
}
