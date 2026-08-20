package com.github.heartratemonitor_compose.ui.settings

import android.content.Intent
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.settings.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.cardShape
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.util.rememberSheetDismissHandler
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveTextButton
import com.github.heartratemonitor_compose.ui.widgets.IconContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: LanguageSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showRestartSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    title = {
                        Text(
                            stringResource(R.string.language_settings),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceBright
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(
                                            com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back
                                        )
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        ExpressiveButton(
                            label = stringResource(
                                com.github.heartratemonitor_compose.ui.widgets.R.string.confirm
                            ),
                            onClick = { showRestartSheet = true }
                        )
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
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

                    LanguageCardList(
                        selectedLanguage = uiState.selectedLanguage,
                        onSelect = { tag ->
                            viewModel.dispatch(
                                LanguageSettingsIntent.SelectLanguage(tag)
                            )
                        }
                    )

                    Spacer(
                        Modifier.height(
                            64.dp + WindowInsets.navigationBars
                                .asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
                StatusBarScrim()
            }
        }
    }

    if (showRestartSheet) {
        RestartConfirmSheet(
            onDismiss = { showRestartSheet = false },
            onRestart = {
                val intent = context.packageManager.getLaunchIntentForPackage(
                    context.packageName
                )?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent != null) {
                    context.startActivity(intent)
                }
                Process.killProcess(Process.myPid())
            }
        )
    }
}

@Composable
private fun LanguageCardList(
    selectedLanguage: String?,
    onSelect: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 自动跟随（始终在最前，独立卡片）
        LanguageCard(
            option = AUTO_FOLLOW_OPTION,
            isSelected = selectedLanguage == null,
            leadingIcon = painterResource(R.drawable.ic_auto_follow),
            onClick = { onSelect(null) }
        )
        LANGUAGE_OPTIONS.forEach { option ->
            LanguageCard(
                option = option,
                isSelected = selectedLanguage == option.tag,
                leadingIcon = null,
                onClick = { onSelect(option.tag) }
            )
        }
    }
}

/**
 * 独立语言选项卡片：28dp 大圆角，8dp 间距。
 *
 * 选中状态通过边框 + 背景变色 + 右侧 [Check] 图标表示。
 * 按压激活反馈使用与 [SettingsItem] 相同的 clip + clickable 方式，
 * ripple 被裁剪在圆角内，与全局设置项保持一致的按压交互体验。
 */
@Composable
private fun LanguageCard(
    option: LanguageOption,
    isSelected: Boolean,
    leadingIcon: androidx.compose.ui.graphics.painter.Painter?,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val cardShape = cardShape(28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                IconContainer(
                    icon = leadingIcon,
                    containerSize = 40.dp,
                    iconSize = 24.dp
                )
            } else {
                Text(
                    text = option.emoji,
                    fontSize = 28.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.nativeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 重启确认 BottomSheet：与其他页面的 BottomSheet 设计风格一致——
 * 使用 [IconContainer] 引导图标 + [titleLarge] 标题 + [bodyMedium] 正文 +
 * [ExpressiveTextButton] / [ExpressiveButton] 按钮组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestartConfirmSheet(
    onDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    val sheetState = rememberExpandedSheetState()
    val dismissWithAnimation = rememberSheetDismissHandler(sheetState, onDismiss)
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
        ) {
            IconContainer(
                icon = painterResource(R.drawable.ic_language_settings),
                containerSize = 48.dp,
                iconSize = 28.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.language_restart_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.language_restart_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveTextButton(
                    label = stringResource(
                        com.github.heartratemonitor_compose.ui.widgets.R.string.cancel
                    ),
                    onClick = dismissWithAnimation
                )
                Spacer(Modifier.width(8.dp))
                ExpressiveButton(
                    label = stringResource(R.string.language_restart),
                    onClick = onRestart
                )
            }
        }
    }
}
