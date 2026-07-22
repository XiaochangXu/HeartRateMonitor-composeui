package com.github.heartratemonitor_compose.ui.webhook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.WebhookTrigger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: WebhookViewModel = viewModel()
    val webhooks by viewModel.webhooks.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf<Pair<Int?, Webhook>?>(null) }
    var testResponse by remember { mutableStateOf<String?>(null) }
    val newWebhookName = stringResource(R.string.new_webhook)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.webhook_title), style = MaterialTheme.typography.headlineSmall) },
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
                }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
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
                    Text(stringResource(R.string.no_webhooks), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                webhooks.forEachIndexed { index, webhook ->
                    WebhookListItem(
                        webhook = webhook,
                        onEdit = { showEditDialog = Pair(index, webhook) },
                        onDelete = { viewModel.deleteWebhook(index) }
                    )
                }
            }
            // 底部留出系统导航栏空间，避免内容被手势条遮挡
            Spacer(Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    showEditDialog?.let { (editIndex, webhook) ->
        WebhookEditDialog(
            webhook = webhook,
            onDismiss = { showEditDialog = null },
            onSave = { updated ->
                val updatedList = if (editIndex != null) {
                    webhooks.toMutableList().apply { this[editIndex] = updated }
                } else {
                    webhooks + updated
                }
                viewModel.saveWebhooks(updatedList)
                showEditDialog = null
            },
            onTest = { testWebhook ->
                viewModel.testWebhook(testWebhook) { result ->
                    testResponse = result
                }
            }
        )
    }

    testResponse?.let { response ->
        AlertDialog(
            onDismissRequest = { testResponse = null },
            title = { Text(stringResource(R.string.webhook_test_response)) },
            text = {
                Column {
                    Text(response, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { testResponse = null }) { Text(stringResource(R.string.close)) }

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
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(28.dp),
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

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, stringResource(R.string.cd_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error)
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
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
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
                TriggerCheckboxes(triggers) { newTriggers -> triggers = newTriggers }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 4
                )

                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("Headers (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 3
                )

                Button(
                    onClick = {
                        onTest(Webhook(name, url, enabled, body, headers, triggers.toMutableList()))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.test_send))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(Webhook(name, url, enabled, body, headers, triggers.toMutableList()))
                    }) {
                        Text(stringResource(R.string.save))

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
