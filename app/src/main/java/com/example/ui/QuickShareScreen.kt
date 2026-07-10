package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.ConnectionData
import com.example.data.TransferScope
import com.example.network.TransferServer
import com.example.utils.NetworkUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun QuickShareScreen(onNavigateToScanner: () -> Unit) {
    val modes = listOf(
        "البيانات والنصوص فقط" to TransferScope.DATA_ONLY,
        "الصور والوثائق الملحقة فقط" to TransferScope.IMAGES_ONLY,
        "الأرشيف الكامل (بيانات + صور)" to TransferScope.FULL_ARCHIVE
    )
    var selectedMode by remember { mutableStateOf(modes[2].second) }
    var showQrDialog by remember { mutableStateOf(false) }
    var connectionJson by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<TransferServer?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            server?.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                if (server == null) {
                    val ip = NetworkUtils.getLocalIpAddress()
                    if (ip != null) {
                        val ports = listOf(8080, 8081, 8082, 8083, 8084, 8085)
                        var started = false
                        for (port in ports) {
                            val newServer = TransferServer(port, selectedMode, File(context.filesDir, "data.db"), null)
                            try {
                                newServer.start()
                                server = newServer
                                connectionJson = Json.encodeToString(ConnectionData(ip, port, selectedMode))
                                showQrDialog = true
                                started = true
                                break
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (!started) {
                            // Handle failure to start server
                        }
                    }
                } else {
                    showQrDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("توليد باركود للمشاركة")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToScanner,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                    Text("امسح الرمز لاستلام البيانات")
                    
                    val qrCodeBitmap = remember(connectionJson) {
                        connectionJson?.let {
                            try {
                                BarcodeEncoder().encodeBitmap(it, BarcodeFormat.QR_CODE, 512, 512)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }

                    qrCodeBitmap?.let {
                        androidx.compose.foundation.Image(
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
