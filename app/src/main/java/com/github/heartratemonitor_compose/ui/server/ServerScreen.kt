<<<<<<< HEAD
package com.github.heartratemonitor_compose.ui.server

import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
=======
﻿package com.github.heartratemonitor_compose.ui.server

import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.heartratemonitor_compose.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onNavigateBack: () -> Unit,
    sharedPreferences: android.content.SharedPreferences
) {
    var httpEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("http_server_enabled", false)) }
    var httpPort by remember { mutableStateOf(sharedPreferences.getInt("http_server_port", 8000).toString()) }
    var wsEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("websocket_server_enabled", false)) }
    var wsPort by remember { mutableStateOf(sharedPreferences.getInt("websocket_server_port", 8001).toString()) }

    val context = LocalContext.current
    val ipAddress by remember {
        derivedStateOf {
<<<<<<< HEAD
            try {
                // 使用 ConnectivityManager 替代已弃用的 WifiManager.connectionInfo
                val cm = context.applicationContext.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = cm?.activeNetwork
                val linkProperties = cm?.getLinkProperties(network)
                val ip = linkProperties?.linkAddresses
                    ?.firstOrNull { it.address is java.net.Inet4Address }
                    ?.address?.hostAddress
                ip ?: "未连接网络"
            } catch (e: SecurityException) {
                "未连接网络"
            }
=======
            val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            android.text.format.Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
        }
    }

    LaunchedEffect(httpEnabled) {
        sharedPreferences.edit().putBoolean("http_server_enabled", httpEnabled).apply()
    }
    LaunchedEffect(wsEnabled) {
        sharedPreferences.edit().putBoolean("websocket_server_enabled", wsEnabled).apply()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text("服务器设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SectionTitle("HTTP 服务器 (被动拉取)")
            ServerCard(
                enabled = httpEnabled,
                onEnabledChange = { httpEnabled = it },
                port = httpPort,
                onPortChange = { httpPort = it },
                portHint = "HTTP 端口",
                portDefault = 8000,
                ipAddress = ipAddress,
                scheme = "http",
                leadingIcon = painterResource(R.drawable.ic_enable_http_server)
            )

            SectionTitle("WebSocket 服务器 (主动推送)")
            ServerCard(
                enabled = wsEnabled,
                onEnabledChange = { wsEnabled = it },
                port = wsPort,
                onPortChange = { wsPort = it },
                portHint = "WebSocket 端口",
                portDefault = 8001,
                ipAddress = ipAddress,
                scheme = "ws",
                leadingIcon = painterResource(R.drawable.ic_enable_websocket_server)
            )

            SectionTitle("服务器状态")
            ServerStatusCard(
                httpEnabled = httpEnabled,
                httpPort = httpPort.toIntOrNull() ?: 8000,
                wsEnabled = wsEnabled,
                wsPort = wsPort.toIntOrNull() ?: 8001,
                ipAddress = ipAddress
            )
        }
    }
}

@Composable
private fun ServerCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    portHint: String,
    portDefault: Int,
    ipAddress: String,
    scheme: String,
    leadingIcon: Painter
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor.copy(alpha = 0.8f) else containerColor
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // MD3 列表项 Leading Icon：24dp + 16dp 间距
                Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "启用 ${if (scheme == "http") "HTTP" else "WebSocket"} 服务器",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        onEnabledChange(it)
                        if (it) onPortChange(portDefault.toString())
                    }
                )
            }

            if (enabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() } && it.length <= 5) {
                            onPortChange(it)
                        }
                    },
                    placeholder = { Text(portHint) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
            }

            if (enabled) {
                Text(
                    text = "访问地址: $scheme://$ipAddress:${if (port.toIntOrNull() != null) port else portDefault}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerStatusCard(
    httpEnabled: Boolean,
    httpPort: Int,
    wsEnabled: Boolean,
    wsPort: Int,
    ipAddress: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HTTP 状态行：根据启用状态切换图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (httpEnabled) R.drawable.ic_http_server_enabled
                        else R.drawable.ic_http_server_disabled
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (httpEnabled) "HTTP 服务器已启用" else "HTTP 服务器已禁用",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            }
            if (httpEnabled) {
                Text(
                    text = "访问地址: http://$ipAddress:$httpPort/heartrate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // WebSocket 状态行：根据启用状态切换图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (wsEnabled) R.drawable.ic_websocket_server_enabled
                        else R.drawable.ic_websocket_server_disabled
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (wsEnabled) "WebSocket 服务器已启用" else "WebSocket 服务器已禁用",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            }
            if (wsEnabled) {
                Text(
                    text = "访问地址: ws://$ipAddress:$wsPort",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp)
    )
}
