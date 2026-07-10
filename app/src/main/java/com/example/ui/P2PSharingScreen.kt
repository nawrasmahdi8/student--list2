package com.example.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.ConnectionData
import com.example.network.DataTransferMode
import com.example.network.P2PServer
import com.example.utils.DataArchiver
import com.example.utils.NetworkUtils
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import android.util.Log

@Composable
fun P2PSharingScreen(viewModel: RegistryViewModel) {
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        QRScannerScreen(
            viewModel = viewModel,
            onResult = { result ->
                // Maybe handle result, e.g. show snackbar
            },
            onBack = { showScanner = false }
        )
    } else {
        QuickShareContent(viewModel, onNavigateToScanner = { showScanner = true })
    }
}

@Composable
fun QuickShareContent(viewModel: RegistryViewModel, onNavigateToScanner: () -> Unit) {
    val modes = listOf(
        "البيانات والنصوص فقط" to DataTransferMode.DATA_ONLY,
        "الصور والوثائق الملحقة فقط" to DataTransferMode.IMAGES_ONLY,
        "الأرشيف الكامل (بيانات + صور)" to DataTransferMode.FULL_ARCHIVE
    )
    var selectedMode by remember { mutableStateOf(modes[2].second) }
    var showQrDialog by remember { mutableStateOf(false) }
    var connectionJson by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<P2PServer?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        onDispose {
            server?.stop()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "المشاركة السريعة ونقل البيانات",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )

            Text(
                text = "اختر نمط النقل:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            modes.forEach { (label, scope) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (scope == selectedMode),
                            onClick = { selectedMode = scope }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (scope == selectedMode),
                        onClick = { selectedMode = scope }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (server != null) {
                        showQrDialog = true
                        return@Button
                    }
                    val ip = NetworkUtils.getLocalIpAddress()
                    if (ip == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تعذر العثور على عنوان IP المحلي")
                        }
                        return@Button
                    }
                    isPreparing = true
                    statusMessage = "Preparing..."

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val zipFile = DataArchiver.zipData(context, selectedMode, viewModel)
                            
                            val md = java.security.MessageDigest.getInstance("SHA-256")
                            zipFile.inputStream().use { input ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    md.update(buffer, 0, read)
                                }
                            }
                            val zipChecksum = md.digest().joinToString("") { "%02x".format(it) }

                            withContext(Dispatchers.Main) {
                                statusMessage = "Generating QR..."
                            }
                            
                            val ports = listOf(8080, 8081, 8082, 8083, 8084, 8085)
                            var started = false
                            for (port in ports) {
                                val newServer = P2PServer(port, context, viewModel, selectedMode, zipFile)
                                try {
                                    newServer.start()
                                    server = newServer
                                    connectionJson = Json.encodeToString(ConnectionData(version = 2, ip = ip, port = port, mode = selectedMode.name, checksum = zipChecksum))
                                    withContext(Dispatchers.Main) {
                                        showQrDialog = true
                                        isPreparing = false
                                        statusMessage = "Waiting for connection..."
                                    }
                                    started = true
                                    break
                                } catch (e: Exception) {
                                    Log.e("P2PSharing", "Failed to start server on port $port", e)
                                }
                            }
                            if (!started) {
                                withContext(Dispatchers.Main) {
                                    isPreparing = false
                                    snackbarHostState.showSnackbar("فشل بدء الخادم المحلي")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("P2PSharing", "Error zipping data", e)
                            withContext(Dispatchers.Main) {
                                isPreparing = false
                                snackbarHostState.showSnackbar("خطأ في تجميع البيانات: ${e.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isPreparing
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(statusMessage)
                } else {
                    Text("توليد باركود للمشاركة")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToScanner,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isPreparing
            ) {
                Text("مسح باركود لسحب البيانات")
            }
        }

        if (showQrDialog) {
            Dialog(onDismissRequest = {
                server?.stop()
                server = null
                showQrDialog = false
            }) {
                Surface(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(statusMessage)
                        
                        val qrCodeBitmap = remember(connectionJson) {
                            connectionJson?.let {
                                try {
                                    BarcodeEncoder().encodeBitmap(it, BarcodeFormat.QR_CODE, 512, 512)
                                } catch (e: Exception) {
                                    Log.e("P2PSharing", "Failed to encode QR", e)
                                    null
                                }
                            }
                        }

                        qrCodeBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(256.dp).padding(16.dp)
                            )
                        }
                        Text(connectionJson ?: "")
                    }
                }
            }
        }
    }
}
