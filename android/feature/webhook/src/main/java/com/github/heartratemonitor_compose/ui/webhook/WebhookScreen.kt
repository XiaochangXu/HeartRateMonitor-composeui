package com.github.heartratemonitor_compose.ui.webhook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heartratemonitor_compose.feature.webhook.R
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.WebhookTrigger
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.util.rememberSheetDismissHandler
import com.github.heartratemonitor_compose.ui.widgets.EmptyState
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveTextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: WebhookViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webhooks = uiState.webhooks
    var showEditDialog by remember { mutableStateOf<Pair<Int?, Webhook>?>(null) }
    var testResponse by remember { mutableStateOf<String?>(null) }
    val newWebhookName = stringResource(R.string.new_webhook)

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
                title = { Text(stringResource(R.string.webhook_title), style = MaterialTheme.typography.headlineSmall) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEditDialog = Pair(null, Webhook(newWebhookName, "")) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_add_webhook))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (webhooks.isEmpty()) {
                // 空状态：整页（扣除顶栏高度）垂直水平居中
                EmptyState(
                    icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_empty_state),
                    message = stringResource(R.string.no_webhooks),
                    modifier = Modifier.padding(top = padding.calculateTopPadding())
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp)
                        .padding(top = padding.calculateTopPadding() + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    webhooks.forEachIndexed { index, webhook ->
                        WebhookListItem(
                            webhook = webhook,
                            onEdit = { showEditDialog = Pair(index, webhook) },
                            onDelete = { viewModel.dispatch(WebhookIntent.Delete(index)) }
                        )
                    }
                    // 底部留出系统导航栏空间，避免内容被手势条遮挡
                    Spacer(Modifier.height(16.dp))
                }
            }
            StatusBarScrim()
        }
    }

    showEditDialog?.let { (editIndex, webhook) ->
        WebhookEditDialog(
            webhook = webhook,
            onDismiss = { showEditDialog = null },
            onSave = { updated ->
                val updatedList = if (editIndex != null) {
                    webhooks.toMutableList().apply { this[editIndex] = updated }.toImmutableList()
                } else {
                    (webhooks + updated).toImmutableList()
                }
                viewModel.dispatch(WebhookIntent.Save(updatedList))
                showEditDialog = null
            },
            onTest = { testWebhook ->
                viewModel.dispatch(WebhookIntent.Test(testWebhook) { result ->
                    testResponse = result
                })
            }
        )
    }

    testResponse?.let { response ->
        val sheetState = rememberExpandedSheetState()
        val dismissWithAnimation = rememberSheetDismissHandler(sheetState) { testResponse = null }
        ModalBottomSheet(
            onDismissRequest = { testResponse = null },
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
                    text = stringResource(com.github.heartratemonitor_compose.data.repository.R.string.webhook_test_response),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = response,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ExpressiveButton(
                        label = stringResource(R.string.close),
                        onClick = dismissWithAnimation
                    )
                }
            }
        }
    }
}

@Composable
private fun WebhookListItem(
    webhook: Webhook,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (webhook.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_webhook),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (webhook.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = webhook.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = webhook.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, stringResource(R.string.cd_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookEditDialog(
    webhook: Webhook,
    onDismiss: () -> Unit,
    onSave: (Webhook) -> Unit,
    onTest: (Webhook) -> Unit
) {
    var name by remember { mutableStateOf(webhook.name) }
    var url by remember { mutableStateOf(webhook.url) }
    var enabled by remember { mutableStateOf(webhook.enabled) }
    var body by remember { mutableStateOf(webhook.body) }
    var headers by remember { mutableStateOf(webhook.headers) }
    var triggers by remember { mutableStateOf(webhook.triggers.toImmutableSet()) }
    val sheetState = rememberExpandedSheetState()
    val dismissWithAnimation = rememberSheetDismissHandler(sheetState, onDismiss)
    val saveWithAnimation = rememberSheetDismissHandler(sheetState) {
        onSave(Webhook(name, url, enabled, body, headers, triggers.toImmutableList()))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.enable_this_webhook), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Text(stringResource(R.string.trigger_types), style = MaterialTheme.typography.labelLarge)
                TriggerCheckboxes(
                    triggers = triggers,
                    onTriggerToggled = { trigger ->
                        triggers = if (triggers.contains(trigger)) (triggers - trigger).toImmutableSet() else (triggers + trigger).toImmutableSet()
                    }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.webhook_url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.webhook_body_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 4
                )

                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text(stringResource(R.string.webhook_headers_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 3
                )

                ExpressiveButton(
                    label = stringResource(R.string.test_send),
                    onClick = {
                        onTest(Webhook(name, url, enabled, body, headers, triggers.toImmutableList()))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveTextButton(
                        label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel),
                        onClick = dismissWithAnimation
                    )
                    Spacer(Modifier.width(8.dp))
                    ExpressiveButton(
                        label = stringResource(R.string.save),
                        onClick = saveWithAnimation
                    )
                }
            }
    }
}

@Composable
private fun TriggerCheckboxes(
    triggers: ImmutableSet<WebhookTrigger>,
    onTriggerToggled: (WebhookTrigger) -> Unit
) {
    val options = listOf(WebhookTrigger.HEART_RATE_UPDATED, WebhookTrigger.CONNECTED, WebhookTrigger.DISCONNECTED)
    val labels = mapOf(
        WebhookTrigger.HEART_RATE_UPDATED to stringResource(R.string.trigger_heart_rate_updated),
        WebhookTrigger.CONNECTED to stringResource(R.string.trigger_connected),
        WebhookTrigger.DISCONNECTED to stringResource(R.string.trigger_disconnected)
    )

    Column {
        options.forEach { trigger ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = triggers.contains(trigger),
                    onCheckedChange = { onTriggerToggled(trigger) }
                )
                Text(labels[trigger] ?: trigger.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
