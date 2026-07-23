package com.github.heartratemonitor_compose.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.github.heartratemonitor_compose.ui.utils.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * 详细信息页面：展示应用图标、名称、简介、版本号，
 * 并集中放置查看开源协议、检查更新、访问项目仓库入口。
 */
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // 当前版本号（去除 'v' 前缀，用于与 Release tag 对比）
    val currentVersion = remember {
        try {
            val raw = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            if (raw != null) raw.removePrefix("v").removePrefix("V") else context.getString(R.string.unknown_version)
        } catch (e: Exception) {
            context.getString(R.string.unknown_version)
        }
    }

    // 检查更新状态
    var updateState by remember { mutableStateOf<Any?>(null) }
    var updateDialog by remember { mutableStateOf<UpdateChecker.Result.UpdateAvailable?>(null) }
    var messageDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
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
                            Box(
                                contentAlignment = Alignment.Center,
                                content = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back)
                                    )
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 11.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

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
                                    messageDialog = context.getString(R.string.up_to_date, result.currentVersion)
                                is UpdateChecker.Result.Error ->
                                    messageDialog = result.message
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

            // 内容延伸到屏幕底部，末尾留出胶囊+系统导航栏空间
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
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

    messageDialog?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { messageDialog = null },
            title = { Text(stringResource(R.string.update_check_title)) },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { messageDialog = null }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
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
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(RoundedCornerShape(28.dp))
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
                        modifier = Modifier
                            .size(84.dp)
                            .clip(with(LocalDensity.current) {
                                SquircleShape(24.dp.toPx().toInt(), cornerSmoothing = 0.67f)
                            })
                    )
                }

                // 中三分之一：应用名 + 简介
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 下三分之一：版本胶囊
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

@Composable
private fun UpdateAvailableDialog(
    currentVersion: String,
    info: UpdateChecker.Result.UpdateAvailable,
    onDismiss: () -> Unit,
    onGoUpdate: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_version_found, info.newVersion)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.current_version_label, currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (info.releaseNotes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.release_notes_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_release_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onGoUpdate) {
                Text(stringResource(R.string.go_update))
            }
        }
    )
}

/**
 * 加载当前应用的启动图标作为 Painter。
 * adaptive-icon 资源无法直接用 painterResource 加载，
 * 因此通过 PackageManager 获取 Drawable 后转为 BitmapPainter。
 */
@Composable
private fun rememberAppIconPainter(): Painter {
    val context = LocalContext.current
    return remember {
        val drawable = context.applicationInfo.loadIcon(context.packageManager)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        BitmapPainter(bitmap.asImageBitmap())
    }
}


