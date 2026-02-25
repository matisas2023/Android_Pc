package com.pcremote.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pcremote.client.ui.viewmodel.MainViewModel

@Composable
fun RootScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    if (!ui.paired && ui.host.isNotBlank()) {
        androidx.compose.runtime.LaunchedEffect(ui.host) {
            vm.autoConnect()
        }
    }
    if (!ui.paired) {
        PairingScreen(
            host = ui.host,
            code = ui.code,
            status = ui.statusText,
            onHostChange = vm::updateHost,
            onCodeChange = vm::updateCode,
            onPair = vm::pair,
            onAutoPair = vm::autoConnect,
        )
    } else {
        DashboardScreen(vm)
    }
}

@Composable
fun PairingScreen(
    host: String,
    code: String,
    status: String,
    onHostChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onAutoPair: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Onboarding + Pairing", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = host, onValueChange = onHostChange, label = { Text("Server IP:port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = code, onValueChange = onCodeChange, label = { Text("Pairing code") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text("Pair") }
        Button(onClick = onAutoPair, modifier = Modifier.fillMaxWidth()) { Text("Автопідключення") }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = vm::loadStatus, modifier = Modifier.fillMaxWidth()) { Text("Connectivity test / Load status") }
            ui.dashboard?.let {
                Text("Machine: ${it.machineName}")
                Text("User: ${it.userName}")
                Text("Uptime: ${it.uptimeSeconds.toInt()} sec")
                Text("IPs: ${it.ips.joinToString()}")
            }
        }

        item {
            Text("Screen", style = MaterialTheme.typography.titleMedium)
            Button(onClick = vm::screenshot, modifier = Modifier.fillMaxWidth()) { Text("Screenshot") }
            ui.screenshot?.let { img ->
                Image(img, contentDescription = "screenshot", modifier = Modifier.fillMaxWidth().size(220.dp))
            }
        }

        item {
            Text("Camera", style = MaterialTheme.typography.titleMedium)
            Button(onClick = vm::cameraPhoto, modifier = Modifier.fillMaxWidth()) { Text("Camera photo") }
            ui.camera?.let { img ->
                Image(img, contentDescription = "camera", modifier = Modifier.fillMaxWidth().size(220.dp))
            }
        }

        item {
            Text("Power", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { vm.power("shutdown") }, modifier = Modifier.weight(1f)) { Text("Shutdown") }
                Button(onClick = { vm.power("restart") }, modifier = Modifier.weight(1f)) { Text("Restart") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { vm.power("sleep") }, modifier = Modifier.weight(1f)) { Text("Sleep") }
                Button(onClick = { vm.power("lock") }, modifier = Modifier.weight(1f)) { Text("Lock") }
            }
        }

        if (ui.statusText.isNotBlank()) {
            item { Text(ui.statusText) }
        }
    }
}
