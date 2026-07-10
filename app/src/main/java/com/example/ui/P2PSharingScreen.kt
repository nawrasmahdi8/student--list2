package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.network.DataTransferMode
import com.example.network.P2PServer

@Composable
fun P2PSharingScreen(viewModel: RegistryViewModel) {
    var selectedMode by remember { mutableStateOf(DataTransferMode.FULL_ARCHIVE) }
    var server by remember { mutableStateOf<P2PServer?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("اختر نمط النقل:", style = MaterialTheme.typography.titleMedium)
        DataTransferMode.values().forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = (selectedMode == mode),
                    onClick = { selectedMode = mode }
                )
                Text(mode.name)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val newServer = P2PServer(8080, context, viewModel, selectedMode)
                newServer.start()
                server = newServer
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("توليد باركود للمشاركة")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { /* Implement scanner */ },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("مسح باركود لسحب البيانات")
        }
    }
}
