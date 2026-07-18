package com.github.heartratemonitor_compose.ui.server

import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
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
import androidx.compose.ui.res.stringResource
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
            try {
                // 使用 ConnectivityManager 替代已弃用的 WifiManager.connectionInfo
                val cm = context.applicationContext.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = cm?.activeNetwork
                val linkProperties = cm?.getLinkProperties(network)
                val ip = linkProperties?.linkAddresses
                    ?.firstOrNull { it.address is java.net.Inet4Address }
                    ?.address?.hostAddress
                ip ?: context.getString(R.string.not_connected_network)
            } catch (e: SecurityException) {
                context.getString(R.string.not_connected_network)
            }
        }
    }

    LaunchedEffect(httpEnabled) {
        sharedPreferences.edit().putBoolean("http_server_enabled", httpEnabled).apply()
    }
    LaunchedEffect(wsEnabled) {
        sharedPreferences.edit().putBoolean("websocket_server_enabled", wsEnabled).apply()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SectionTitle(stringResource(R.string.http_server_passive))
            ServerCard(
                enabled = httpEnabled,
                onEnabledChange = { httpEnabled = it },
                port = httpPort,
                onPortChange = { httpPort = it },
                portHint = stringResource(R.string.http_port_hint),
                portDefault = 8000,
                ipAddress = ipAddress,
                scheme = "http",
                leadingIcon = painterResource(R.drawable.ic_enable_http_server)
            )

            SectionTitle(stringResource(R.string.websocket_server_active))
            ServerCard(
                enabled = wsEnabled,
                onEnabledChange = { wsEnabled = it },
                port = wsPort,
                onPortChange = { wsPort = it },
                portHint = stringResource(R.string.websocket_port_hint),
                portDefault = 8001,
                ipAddress = ipAddress,
                scheme = "ws",
                leadingIcon = painterResource(R.drawable.ic_enable_websocket_server)
            )

            SectionTitle(stringResource(R.string.server_status))
            ServerStatusCard(
                httpEnabled = httpEnabled,
                httpPort = httpPort.toIntOrNull() ?: 8000,
                wsEnabled = wsEnabled,
                wsPort = wsPort.toIntOrNull() ?: 8001,
                ipAddress = ipAddress
            )
            // 底部留出系统导航栏空间，避免内容被手势条遮挡
            Spacer(Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
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
    val context = LocalContext.current
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    text = context.getString(R.string.enable_server_format, if (scheme == "http") "HTTP" else "WebSocket"),
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
                    text = context.getString(R.string.access_url_format, scheme, ipAddress, if (port.toIntOrNull() != null) port else portDefault.toString()),
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
    val context = LocalContext.current
    // 用 Column + 2dp 间隔取代单 Card + Divider，与设置页分段式卡片组风格统一
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                        text = if (httpEnabled) stringResource(R.string.http_enabled_status) else stringResource(R.string.http_disabled_status),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal
                    )
                }
                if (httpEnabled) {
                    Text(
                        text = context.getString(R.string.http_access_url, ipAddress, httpPort),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                        text = if (wsEnabled) stringResource(R.string.ws_enabled_status) else stringResource(R.string.ws_disabled_status),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal
                    )
                }
                if (wsEnabled) {
                    Text(
                        text = context.getString(R.string.ws_access_url, ipAddress, wsPort),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 2.dp)
    )
}
