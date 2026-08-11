package com.github.heartratemonitor_compose.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.main.MainActivity
import com.github.heartratemonitor_compose.ui.util.MarkdownText
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.widgets.IconContainer

import kotlinx.coroutines.launch


/** messageDialog 的展示数据：subtitle 为标题下副文本，cardContent 为卡片内容，renderAsMarkdown 控制是否 Markdown 渲染 */
private data class MessageDialogData(
    val subtitle: String?,
    val cardContent: String,
    val renderAsMarkdown: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDetailsScreen(
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenExternal: (Intent) -> Unit,
    showToast: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

     val currentVersion = remember {
        try {
            val raw = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            if (raw != null) raw.removePrefix("v").removePrefix("V") else context.getString(R.string.unknown_version)
        } catch (e: Exception) {
            context.getString(R.string.unknown_version)
        }
    }

     var updateState by remember { mutableStateOf<Any?>(null) }
    var updateDialog by remember { mutableStateOf<UpdateChecker.Result.UpdateAvailable?>(null) }
    var messageDialog by remember { mutableStateOf<MessageDialogData?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                title = {
                    Text(
                        text = stringResource(R.string.about_details_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))

            // 顶部渐变卡片
            AboutHeaderCard(
                currentVersion = currentVersion,
                onCopyVersion = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(context.getString(R.string.version), currentVersion)
                    clipboard.setPrimaryClip(clip)
                    showToast(context.getString(R.string.version_copied))
                }
            )

            // 功能入口
            AboutActionGroup(
                onOpenLicense = { onNavigate("license") },
                onOpenPrivacy = { onNavigate("privacy") },
                onCheckUpdate = {
                    if (updateState is UpdateChecker.Result.UpdateAvailable) {
                        updateDialog = updateState as UpdateChecker.Result.UpdateAvailable
                    } else {
                        updateState = UpdateChecker.Result.Error(context.getString(R.string.checking_update))
                        scope.launch {
                            val result = UpdateChecker.check(context, currentVersion)
                            updateState = result
                            when (result) {
                                is UpdateChecker.Result.UpdateAvailable -> updateDialog = result
                                is UpdateChecker.Result.UpToDate ->
                                    messageDialog = MessageDialogData(
                                        subtitle = context.getString(R.string.up_to_date, result.currentVersion),
                                        cardContent = result.releaseNotes,
                                        renderAsMarkdown = true
                                    )
                                is UpdateChecker.Result.Error ->
                                    messageDialog = MessageDialogData(
                                        subtitle = null,
                                        cardContent = result.message,
                                        renderAsMarkdown = false
                                    )
                            }
                        }
                    }
                },
                onOpenRepository = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/XiaochangXu/HeartRateMonitor-composeui")
                    )
                    MainActivity.suppressHideForExternalLaunch = true
                    onOpenExternal(intent)
                },
                updateTitle = if (updateState is UpdateChecker.Result.Error &&
                    (updateState as UpdateChecker.Result.Error).message == context.getString(R.string.checking_update)
                ) context.getString(R.string.checking_update) else stringResource(R.string.check_update)
            )

            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
    }

    updateDialog?.let { info ->
        UpdateAvailableDialog(
            currentVersion = currentVersion,
            info = info,
            onDismiss = { updateDialog = null },
            onGoUpdate = {
                updateDialog = null
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                MainActivity.suppressHideForExternalLaunch = true
                onOpenExternal(intent)
            }
        )
    }

    messageDialog?.let { data ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { messageDialog = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.65f)
                    .padding(24.dp)
            ) {
                // 检查更新图标（复用详细信息页面的 ic_check_update）
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
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
                androidx.compose.material3.TextButton(
                    onClick = { messageDialog = null },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun AboutHeaderCard(
    currentVersion: String,
    onCopyVersion: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = rememberAppIconPainter()
    val appName = stringResource(R.string.app_name)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(MaterialTheme.shapes.extraLarge)
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryContainer,
                                secondaryContainer.copy(alpha = 0.85f)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = appIcon,
                        contentDescription = appName,
                        modifier = Modifier.size(84.dp)
                    )
                }

             Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                  Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onCopyVersion() })
                            },
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = context.getString(R.string.version_format, currentVersion),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutActionGroup(
    onOpenLicense: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenRepository: () -> Unit,
    updateTitle: String
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = onOpenLicense) {
            SettingsLink(
                title = stringResource(R.string.open_source_license),
                subtitle = stringResource(R.string.subtitle_open_source_license),
                leadingIcon = painterResource(R.drawable.ic_license),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = onOpenPrivacy) {
            SettingsLink(
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.subtitle_privacy_policy),
                leadingIcon = painterResource(R.drawable.ic_privacy),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = onCheckUpdate) {
            SettingsLink(
                title = updateTitle,
                subtitle = stringResource(R.string.subtitle_check_update),
                leadingIcon = painterResource(R.drawable.ic_check_update),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true, onClick = onOpenRepository) {
            SettingsLink(
                title = stringResource(R.string.github_repo),
                subtitle = stringResource(R.string.subtitle_github_repo),
                leadingIcon = painterResource(R.drawable.ic_github_repo),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateAvailableDialog(
    currentVersion: String,
    info: UpdateChecker.Result.UpdateAvailable,
    onDismiss: () -> Unit,
    onGoUpdate: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
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
            // 检查更新图标（复用详细信息页面的 ic_check_update）
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
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
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
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.confirm))
                }
                androidx.compose.material3.TextButton(onClick = onGoUpdate) {
                    Text(stringResource(R.string.go_update))
                }
            }
        }
    }
}


@Composable
private fun rememberAppIconPainter(): Painter = painterResource(R.drawable.about)


