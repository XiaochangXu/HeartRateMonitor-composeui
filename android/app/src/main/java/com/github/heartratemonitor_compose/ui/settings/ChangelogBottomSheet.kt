package com.github.heartratemonitor_compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.util.MarkdownText
import com.github.heartratemonitor_compose.ui.widgets.IconContainer

/**
 * 更新日志 BottomSheet。
 *
 * 复用检查更新 BottomSheet 的布局：
 * - IconContainer（ic_check_update 图标）
 * - 大标题「更新日志」24sp / 700
 * - 副标题「当前版本 vX.X」16sp / 500
 * - 圆角卡片容器 + Markdown 渲染 + 可滚动
 * - 确认按钮
 *
 * 用于应用首次安装/更新后自动弹出，展示本地 [R.raw.changelog] 内容。
 *
 * @param changelogContent Markdown 格式的更新日志文本
 * @param currentVersion   当前应用版本号（不含 "v" 前缀）
 * @param onDismiss        关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(
    changelogContent: String,
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.65f)
                .padding(24.dp)
        ) {
            // 更新日志图标（复用检查更新的 ic_check_update）
            IconContainer(
                icon = painterResource(R.drawable.ic_check_update),
                containerSize = 48.dp,
                iconSize = 28.dp
            )
            Spacer(Modifier.height(12.dp))
            // 「更新日志」大标题
            Text(
                text = stringResource(R.string.changelog_title),
                fontSize = 24.sp,
                fontWeight = FontWeight(700)
            )
            // 副标题「当前版本 vX.X」
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.current_version_subtitle, currentVersion),
                fontSize = 16.sp,
                fontWeight = FontWeight(500)
            )
            Spacer(Modifier.height(16.dp))
            // 内容区域：圆角卡片容器 + Markdown 渲染
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (changelogContent.isNotEmpty()) {
                        MarkdownText(markdown = changelogContent)
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
