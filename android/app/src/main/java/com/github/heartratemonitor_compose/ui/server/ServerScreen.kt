package com.github.heartratemonitor_compose.ui.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onNavigateBack: () -> Unit,
    settings: SettingsRepository
) {
    var httpEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HTTP_SERVER_ENABLED, false)) }
    var httpPort by remember { mutableStateOf(settings.getInt(PrefsKeys.HTTP_SERVER_PORT, 8000).toString()) }
    var wsEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, false)) }
    var wsPort by remember { mutableStateOf(settings.getInt(PrefsKeys.WEBSOCKET_SERVER_PORT, 8001).toString()) }

    val context = LocalContext.current
    val ipAddressProvider = remember { context.applicationContext.appContainer.ipAddressProvider }
    val ipAddress by remember {
        derivedStateOf {
            ipAddressProvider.getLocalIpAddress() ?: context.getString(R.string.not_connected_network)
        }
    }

    LaunchedEffect(httpEnabled) {
        settings.setBoolean(PrefsKeys.HTTP_SERVER_ENABLED, httpEnabled)
    }
    LaunchedEffect(wsEnabled) {
        settings.setBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, wsEnabled)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text(stringResource(R.string.server_settings), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.cd_back))
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding()))

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

            ServerStatusCard(
                httpEnabled = httpEnabled,
                httpPort = httpPort.toIntOrNull() ?: 8000,
                wsEnabled = wsEnabled,
                wsPort = wsPort.toIntOrNull() ?: 8001,
                ipAddress = ipAddress
            )
            
            Spacer(Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
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
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
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
                IconContainer(icon = leadingIcon)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = context.getString(R.string.enable_server_format, if (scheme == "http") "HTTP" else "WebSocket"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
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

            // 端口输入框与访问URL用 AnimatedVisibility 平滑展开/收起
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(250)),
                exit = shrinkVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Column {
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

                    Text(
                        text = context.getString(R.string.access_url_format, scheme, ipAddress, if (port.toIntOrNull() != null) port else portDefault.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconContainer(
                        icon = painterResource(
                            if (httpEnabled) R.drawable.ic_http_server_enabled
                            else R.drawable.ic_http_server_disabled
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (httpEnabled) stringResource(R.string.http_enabled_status) else stringResource(R.string.http_disabled_status),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
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
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconContainer(
                        icon = painterResource(
                            if (wsEnabled) R.drawable.ic_websocket_server_enabled
                            else R.drawable.ic_websocket_server_disabled
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (wsEnabled) stringResource(R.string.ws_enabled_status) else stringResource(R.string.ws_disabled_status),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
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
