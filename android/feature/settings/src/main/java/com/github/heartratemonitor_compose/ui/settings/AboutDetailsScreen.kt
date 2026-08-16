package com.github.heartratemonitor_compose.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle


/**
 * 关于详情页：
 * - 版本卡片与功能入口见 [AboutHeaderCard] / [AboutActionGroup]（AboutSections.kt）
 * - 检查更新弹窗见 [UpdateCheckSheet]（UpdateCheckSheet.kt）
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
    val viewModel: AboutDetailsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    // 弹窗显隐属 UI 瞬时态；检查流程与结果归 viewModel.uiState
    var updateSheetVisible by remember { mutableStateOf(false) }
    val updateSheet: UpdateSheetState? = if (!updateSheetVisible) {
        null
    } else {
        val result = uiState.updateResult
        if (uiState.isChecking || result == null) {
            UpdateSheetState.Checking
        } else {
            when (result) {
                is UpdateChecker.Result.UpdateAvailable -> UpdateSheetState.Available(result)
                is UpdateChecker.Result.UpToDate -> UpdateSheetState.Message(
                    MessageDialogData(
                        subtitle = context.getString(R.string.up_to_date, result.currentVersion),
                        cardContent = result.releaseNotes,
                        renderAsMarkdown = true
                    )
                )
                is UpdateChecker.Result.Error -> UpdateSheetState.Message(
                    MessageDialogData(
                        subtitle = null,
                        cardContent = result.message,
                        renderAsMarkdown = false
                    )
                )
            }
        }
    }

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
                                    contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back)
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
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

            // 维护者卡片
            MaintainerCard()

            // 功能入口
            AboutActionGroup(
                onOpenLicense = { onNavigate("license") },
                onOpenPrivacy = { onNavigate("privacy") },
                onCheckUpdate = {
                    // 点击立即弹出 BottomSheet（居中显示加载指示器），结果到达后同一 Sheet 切换内容
                    updateSheetVisible = true
                    viewModel.dispatch(AboutDetailsIntent.CheckUpdate(currentVersion))
                },
                onOpenRepository = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/XiaochangXu/HeartRateMonitor-composeui")
                    )
                    // 外部启动抑制标志由 MainActivity.onOpenExternal 统一置位，此处不再重复
                    onOpenExternal(intent)
                },
                updateTitle = if (uiState.updateResult is UpdateChecker.Result.Error &&
                    (uiState.updateResult as UpdateChecker.Result.Error).message == context.getString(R.string.checking_update)
                ) context.getString(R.string.checking_update) else stringResource(R.string.check_update)
            )

            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
    }

    updateSheet?.let { state ->
        UpdateCheckSheet(
            state = state,
            currentVersion = currentVersion,
            onDismiss = {
                // 检查中关闭时取消检查任务，避免结果返回后重新弹出 Sheet
                updateSheetVisible = false
                viewModel.dispatch(AboutDetailsIntent.CancelCheck)
            },
            onGoUpdate = {
                val info = (state as? UpdateSheetState.Available)?.info
                updateSheetVisible = false
                viewModel.dispatch(AboutDetailsIntent.CancelCheck)
                if (info != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                    onOpenExternal(intent)
                }
            }
        )
    }
}
