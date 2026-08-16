package com.github.heartratemonitor_compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.util.MarkdownText
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.cardShape
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.widgets.IconContainer

/** messageDialog 的展示数据：subtitle 为标题下副文本，cardContent 为卡片内容，renderAsMarkdown 控制是否 Markdown 渲染 */
internal data class MessageDialogData(
    val subtitle: String?,
    val cardContent: String,
    val renderAsMarkdown: Boolean
)

internal sealed interface UpdateSheetState {
    data object Checking : UpdateSheetState
    data class Available(val info: UpdateChecker.Result.UpdateAvailable) : UpdateSheetState
    data class Message(val data: MessageDialogData) : UpdateSheetState
}

/**
 * 检查更新统一 BottomSheet：按 [UpdateSheetState] 切换内容。
 * Checking → 居中加载指示器；Available / Message → 结果内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateCheckSheet(
    state: UpdateSheetState,
    currentVersion: String,
    onDismiss: () -> Unit,
    onGoUpdate: () -> Unit
) {
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape
    ) {
        when (state) {
            is UpdateSheetState.Checking -> CheckingContent()
            is UpdateSheetState.Available -> UpdateAvailableContent(
                info = state.info,
                currentVersion = currentVersion,
                onConfirm = onDismiss,
                onGoUpdate = onGoUpdate
            )
            is UpdateSheetState.Message -> UpdateMessageContent(
                data = state.data,
                onConfirm = onDismiss
            )
        }
    }
}

/** 检查中：居中显示 ContainedLoadingIndicator */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(64.dp)
        )
    }
}

/** 有可用更新：新版本信息 + Release Notes */
@Composable
private fun UpdateAvailableContent(
    info: UpdateChecker.Result.UpdateAvailable,
    currentVersion: String,
    onConfirm: () -> Unit,
    onGoUpdate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.65f)
            .padding(24.dp)
    ) {
        IconContainer(
            icon = painterResource(R.drawable.ic_check_update),
            containerSize = 48.dp,
            iconSize = 28.dp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.new_version_found, info.newVersion),
            fontSize = 16.sp,
            fontWeight = FontWeight(500)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.current_version_label, currentVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        // 内容区域：圆角卡片容器 + Markdown 渲染 Release Notes
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = cardShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceBright
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (info.releaseNotes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.release_notes_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    MarkdownText(markdown = info.releaseNotes)
                } else {
                    Text(
                        text = stringResource(R.string.no_release_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onConfirm) {
                Text(stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.confirm))
            }
            TextButton(onClick = onGoUpdate) {
                Text(stringResource(R.string.go_update))
            }
        }
    }
}

/** 已是最新 / 出错：结果消息展示 */
@Composable
private fun UpdateMessageContent(
    data: MessageDialogData,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.65f)
            .padding(24.dp)
    ) {
        IconContainer(
            icon = painterResource(R.drawable.ic_check_update),
            containerSize = 48.dp,
            iconSize = 28.dp
        )
        Spacer(Modifier.height(12.dp))
        // 「检查更新」大标题
        Text(
            text = stringResource(R.string.update_check_title),
            fontSize = 24.sp,
            fontWeight = FontWeight(700)
        )
        // 副标题（如「当前已是最新版本 v4.5」），与标题同样样式
        if (data.subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = data.subtitle,
                fontSize = 16.sp,
                fontWeight = FontWeight(500)
            )
        }
        Spacer(Modifier.height(16.dp))
        // 内容区域：圆角卡片容器
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = cardShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceBright
        ) {
            if (data.renderAsMarkdown) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (data.cardContent.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.release_notes_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        MarkdownText(markdown = data.cardContent)
                    } else {
                        Text(
                            text = stringResource(R.string.no_release_notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = data.cardContent,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.confirm))
        }
    }
}
