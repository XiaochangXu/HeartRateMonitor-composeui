package com.github.heartratemonitor_compose.ui.webhook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.WebhookTrigger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookScreen(
    onNavigateBack: () -> Unit,
    context: android.content.Context
) {
    val webhookManager = remember { WebhookManager(context) }
    var webhooks by remember { mutableStateOf(mutableListOf<Webhook>().toMutableStateList()) }
    var showEditDialog by remember { mutableStateOf<Pair<Int?, Webhook>?>(null) }
    var showSyncConfirm by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testResponse by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        webhooks.clear()
        webhooks.addAll(webhookManager.getWebhooks().toMutableStateList())
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text("Webhook 设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSyncConfirm = true }) {
                        Icon(Icons.Default.Add, "从 GitHub 同步")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEditDialog = Pair(null, Webhook("新的 Webhook", "")) },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, "新增 Webhook")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (webhooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无 Webhook，点击 + 添加", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                webhooks.forEachIndexed { index, webhook ->
                    WebhookListItem(
                        webhook = webhook,
                        onEdit = { showEditDialog = Pair(index, webhook) },
                        onDelete = {
                            webhooks.removeAt(index)
                            webhookManager.saveWebhooks(webhooks)
                        }
                    )
                }
            }
        }
    }

    // 编辑/新增对话框
    showEditDialog?.let { (editIndex, webhook) ->
        WebhookEditDialog(
            webhook = webhook,
            onDismiss = { showEditDialog = null },
            onSave = { updated ->
                if (editIndex != null) {
                    webhooks[editIndex] = updated
                } else {
                    webhooks.add(updated)
                }
                webhookManager.saveWebhooks(webhooks)
                showEditDialog = null
            },
            onTest = { testWebhook ->
                webhookManager.testWebhook(testWebhook) { result ->
                    testResponse = result
                }
            }
        )
    }

    // 同步确认对话框
    if (showSyncConfirm) {
        AlertDialog(
            onDismissRequest = { showSyncConfirm = false },
            title = { Text("确认同步") },
            text = { Text("这将从 GitHub 下载官方预设，并覆盖你本地的 config_webhook.json 文件。\n\n你所有自定义的 Webhook 都将丢失。确定要继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showSyncConfirm = false
                    webhookManager.syncFromGithub { success, message ->
                        syncResult = success to message
                        if (success) {
                            webhooks.clear()
                            webhooks.addAll(webhookManager.getWebhooks().toMutableStateList())
                        }
                    }
                }) { Text("确定", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSyncConfirm = false }) { Text("取消") }
            }
        )
    }

    // 同步结果对话框
    syncResult?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { syncResult = null },
            title = { Text("同步结果") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { syncResult = null }) { Text("好的") }
            }
        )
    }

    // 测试响应对话框
    testResponse?.let { response ->
        AlertDialog(
            onDismissRequest = { testResponse = null },
            title = { Text("Webhook 测试响应") },
            text = {
                Column {
                    Text(response, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { testResponse = null }) { Text("关闭") }
            }
        )
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (webhook.enabled) "✓" else "✗",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (webhook.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // 名称和 URL
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = webhook.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
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

            // 编辑按钮
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
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
    var triggers by remember { mutableStateOf(webhook.triggers.toMutableSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 启用开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用此 Webhook", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                // 触发器
                Text("触发器类型 (可多选)", style = MaterialTheme.typography.labelLarge)
                TriggerCheckboxes(triggers) { newTriggers -> triggers = newTriggers }

                // 名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                // URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Body
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 4
                )

                // Headers
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("Headers (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 3
                )

                // 测试按钮
                Button(
                    onClick = {
                        onTest(Webhook(name, url, enabled, body, headers, triggers.toMutableList()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("测试发送 (心率: 88)")
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(Webhook(name, url, enabled, body, headers, triggers.toMutableList()))
                    }) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerCheckboxes(
    triggers: MutableSet<WebhookTrigger>,
    onTriggersChanged: (MutableSet<WebhookTrigger>) -> Unit
) {
    val options = listOf(WebhookTrigger.HEART_RATE_UPDATED, WebhookTrigger.CONNECTED, WebhookTrigger.DISCONNECTED)
    val labels = mapOf(
        WebhookTrigger.HEART_RATE_UPDATED to "心率刷新时",
        WebhookTrigger.CONNECTED to "设备连接时",
        WebhookTrigger.DISCONNECTED to "设备断开时"
    )

    Column {
        options.forEach { trigger ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = triggers.contains(trigger),
                    onCheckedChange = { checked ->
                        val newTriggers = triggers.toMutableSet()
                        if (checked) newTriggers.add(trigger) else newTriggers.remove(trigger)
                        onTriggersChanged(newTriggers)
                    }
                )
                Text(labels[trigger] ?: trigger.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
