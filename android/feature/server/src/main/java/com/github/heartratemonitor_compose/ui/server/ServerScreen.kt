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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.server.R
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import com.github.heartratemonitor_compose.ui.util.SegmentBottomShape
import com.github.heartratemonitor_compose.ui.util.SegmentTopShape
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: ServerSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 端口输入草稿：纯瞬时态保留 UI 层（判定标准 4），提交经 VM 校验后写入；
    // null 表示未编辑，展示跟随 uiState 的已持久化值
    var httpPortDraft by remember { mutableStateOf<String?>(null) }
    var wsPortDraft by remember { mutableStateOf<String?>(null) }
    val httpPortText = httpPortDraft ?: uiState.httpPort.toString()
    val wsPortText = wsPortDraft ?: uiState.wsPort.toString()

    val context = LocalContext.current
    // 本机 IP 随 uiState 一次性快照下行（进入页面时静态取值，与原语义一致）
    val ipAddress = uiState.ipAddress ?: context.getString(R.string.not_connected_network)

    // 离开页面时兜底提交草稿（覆盖未按 IME Done 直接返回的场景）。
    // DisposableEffect(Unit) 的 effect 块仅在首次组合执行一次，闭包会捕获首次组合的
    // httpPortText/wsPortText（即草稿为 null 时的旧端口值），必须经 rememberUpdatedState
    // 转发最新值，否则编辑后的草稿在返回页面时被静默丢弃。
    val latestHttpPortText by rememberUpdatedState(httpPortText)
    val latestWsPortText by rememberUpdatedState(wsPortText)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.dispatch(ServerSettingsIntent.CommitHttpPort(latestHttpPortText))
            viewModel.dispatch(ServerSettingsIntent.CommitWsPort(latestWsPortText))
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back))
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
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = padding.calculateTopPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ServerCard(
                enabled = uiState.httpEnabled,
                onEnabledChange = {
                    viewModel.dispatch(ServerSettingsIntent.SetHttpEnabled(it))
                    httpPortDraft = null
                },
                port = httpPortText,
                onPortChange = { httpPortDraft = it },
                onPortCommit = {
                    viewModel.dispatch(ServerSettingsIntent.CommitHttpPort(httpPortText))
                    httpPortDraft = null
                },
                portHint = stringResource(R.string.http_port_hint),
                portDefault = 8000,
                ipAddress = ipAddress,
                scheme = "http",
                leadingIcon = painterResource(R.drawable.ic_enable_http_server)
            )

            ServerCard(
                enabled = uiState.wsEnabled,
                onEnabledChange = {
                    viewModel.dispatch(ServerSettingsIntent.SetWsEnabled(it))
                    wsPortDraft = null
                },
                port = wsPortText,
                onPortChange = { wsPortDraft = it },
                onPortCommit = {
                    viewModel.dispatch(ServerSettingsIntent.CommitWsPort(wsPortText))
                    wsPortDraft = null
                },
                portHint = stringResource(R.string.websocket_port_hint),
                portDefault = 8001,
                ipAddress = ipAddress,
                scheme = "ws",
                leadingIcon = painterResource(R.drawable.ic_enable_websocket_server)
            )

            ServerStatusCard(
                httpEnabled = uiState.httpEnabled,
                httpPort = uiState.httpPort,
                wsEnabled = uiState.wsEnabled,
                wsPort = uiState.wsPort,
                ipAddress = ipAddress,
                httpRunning = uiState.httpRunning,
                wsRunning = uiState.wsRunning
            )
            
            Spacer(Modifier.height(16.dp))
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
    onPortCommit: () -> Unit,
    portHint: String,
    portDefault: Int,
    ipAddress: String,
    scheme: String,
    leadingIcon: Painter
) {
    val context = LocalContext.current
    val containerColor = MaterialTheme.colorScheme.surfaceBright

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
                    // 开启时的默认端口重置与落盘由 ViewModel 统一处理
                    onCheckedChange = onEnabledChange
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
                        keyboardActions = KeyboardActions(
                            onDone = { onPortCommit() }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    val displayPort = if (port.toIntOrNull() != null) port else portDefault.toString()
                    // HTTP 需带 /heartrate 路径（实际 API 路由），WS 不带路径
                    val path = if (scheme == "http") "/heartrate" else ""
                    Text(
                        text = context.getString(R.string.access_url_format, scheme, ipAddress, displayPort) + path,
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
    ipAddress: String,
    httpRunning: Boolean?,
    wsRunning: Boolean?
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SegmentTopShape,
            color = MaterialTheme.colorScheme.surfaceBright,
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
                    when (httpRunning) {
                        true -> Text(
                            text = context.getString(R.string.http_access_url, ipAddress, httpPort),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                        )
                        false -> Text(
                            // 设置已启用但服务器实际未运行（端口冲突等）
                            text = stringResource(R.string.server_start_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                        )
                        null -> {} // 启动中：不显示副文本，避免闪烁
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SegmentBottomShape,
            color = MaterialTheme.colorScheme.surfaceBright,
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
                    when (wsRunning) {
                        true -> Text(
                            text = context.getString(R.string.ws_access_url, ipAddress, wsPort),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                        )
                        false -> Text(
                            // 设置已启用但服务器实际未运行（端口冲突等）
                            text = stringResource(R.string.server_start_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp, start = 40.dp)
                        )
                        null -> {} // 启动中：不显示副文本，避免闪烁
                    }
                }
            }
        }
    }
}
