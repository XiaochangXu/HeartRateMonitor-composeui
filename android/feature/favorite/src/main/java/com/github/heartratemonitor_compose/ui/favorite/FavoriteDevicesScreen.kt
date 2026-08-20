package com.github.heartratemonitor_compose.ui.favorite

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heartratemonitor_compose.feature.favorite.R
import com.github.heartratemonitor_compose.data.model.FavoriteDeviceInfo
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.util.collectWhenActive
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.util.rememberSheetDismissHandler
import com.github.heartratemonitor_compose.ui.widgets.EmptyState
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButtonStyle
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveTextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteDevicesScreen(
    onNavigateBack: () -> Unit,
    isInTab: Boolean = false,
    isActive: Boolean = true
) {
    val viewModel: FavoriteDevicesViewModel = hiltViewModel()
    // 非前台时暂停订阅：ViewModel 内 Room 流常驻维护状态，UI 不随数据变化重组
    val uiState by viewModel.uiState.collectWhenActive(isActive)
    val devices = uiState.devices
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<FavoriteDeviceInfo?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.favorite_devices), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    // Tab 模式下不显示返回按钮
                    if (!isInTab) {
                        IconButton(onClick = onNavigateBack) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceBright
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back))
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        enabled = devices.isNotEmpty()
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_forever),
                                    contentDescription = stringResource(R.string.cd_clear_all_favorites),
                                    tint = if (devices.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                           else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                // 数据加载中：不渲染任何内容，避免空状态 icon 闪烁
            } else if (devices.isEmpty()) {
                EmptyState(
                    icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_empty_state),
                    message = stringResource(R.string.no_favorite_devices),
                    modifier = Modifier.padding(top = padding.calculateTopPadding())
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 16.dp,
                    bottom = 8.dp
                    )
                ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onDelete = {
                            deviceToDelete = device
                            showDeleteDialog = true
                        }
                    )
                }
            }
            }
            StatusBarScrim()
        }
    }

    if (showDeleteDialog && deviceToDelete != null) {
        val sheetState = rememberExpandedSheetState()
        val dismiss = rememberSheetDismissHandler(sheetState) { showDeleteDialog = false }
        ModalBottomSheet(
            onDismissRequest = { showDeleteDialog = false },
            sheetState = sheetState,
            shape = SheetTopShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.delete_favorite_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.delete_favorite_confirm, deviceToDelete!!.name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ExpressiveTextButton(
                        label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel),
                        onClick = dismiss
                    )
                    Spacer(Modifier.width(8.dp))
                    ExpressiveButton(
                        label = stringResource(R.string.delete),
                        onClick = {
                            val id = deviceToDelete!!.id
                            viewModel.dispatch(FavoriteDevicesIntent.RemoveFavorite(id))
                            deviceToDelete = null
                            dismiss()
                        },
                        style = ExpressiveButtonStyle.Danger
                    )
                }
            }
        }
    }

    if (showClearAllDialog) {
        val sheetState = rememberExpandedSheetState()
        val dismiss = rememberSheetDismissHandler(sheetState) { showClearAllDialog = false }
        ModalBottomSheet(
            onDismissRequest = { showClearAllDialog = false },
            sheetState = sheetState,
            shape = SheetTopShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.clear_all_favorites_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.clear_all_favorites_confirm, devices.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ExpressiveTextButton(
                        label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel),
                        onClick = dismiss
                    )
                    Spacer(Modifier.width(8.dp))
                    ExpressiveButton(
                        label = stringResource(R.string.delete_all),
                        onClick = {
                            viewModel.dispatch(FavoriteDevicesIntent.ClearAll)
                            dismiss()
                        },
                        style = ExpressiveButtonStyle.Danger
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: FavoriteDeviceInfo,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
