package com.github.heartratemonitor_compose.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Debug
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FairMemoryScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.fair_memory_title),
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.overview),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = stringResource(R.string.fair_memory_overview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Text(
                text = stringResource(R.string.how_it_works),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = stringResource(R.string.fair_memory_how_it_works),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Text(
                text = stringResource(R.string.broadcast_actions),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            // 分段式卡片组：用 Column + 2dp 间隔取代单 Card + Divider
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.memory_warning_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.memory_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_kill_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.app_kill_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.app_adaptation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = stringResource(R.string.fair_memory_adaptation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 设备内存
            DeviceMemorySection()

            Text(
                text = stringResource(R.string.reference_docs),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            // 分段式卡片组：用 Column + 2dp 间隔取代独立 Card
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DocumentationLinkCard(
                    title = "OPPO",
                    description = stringResource(R.string.oppo_desc),
                    url = "https://open.oppomobile.com/documentation/page/info?id=13825",
                    isFirst = true
                )
                DocumentationLinkCard(
                    title = "vivo",
                    description = stringResource(R.string.vivo_desc),
                    url = "https://dev.vivo.com.cn/wap/documentCenter/doc/1013"
                )
                DocumentationLinkCard(
                    title = "小米",
                    description = stringResource(R.string.xiaomi_desc),
                    url = "https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2304"
                )
                DocumentationLinkCard(
                    title = "Android 17 Beta",
                    description = "The Fourth Beta of Android 17",
                    url = "https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html?m=1",
                    isLast = true
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}


@Composable
private fun DeviceMemorySection() {
    val context = LocalContext.current
    
    // 通过 LaunchedEffect + delay(1000) 实现每秒刷新
    var appMemoryMb by remember { mutableFloatStateOf(0f) }
    var totalMemoryGb by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            try {
                // 获取 App 内存（Debug.getMemoryInfo 将对象传入并填充数据）
                // getTotalPss() 返回 KB，除以 1024 转为 MB
                val debugMemInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(debugMemInfo)
                appMemoryMb = debugMemInfo.getTotalPss() / 1024f
                
                // 获取系统总内存（字节），除以 1024³ 转为 GB
                val sysMemInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(sysMemInfo)
                totalMemoryGb = (sysMemInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
            } catch (_: Exception) {
                // 静默失败，保持上次读取的值
            }
            
            delay(1000)
        }
    }
    
    // 统一单位后再算百分比：appMemoryMb / totalMemoryGbMB * 100
    val usagePercent = if (totalMemoryGb > 0f) (appMemoryMb / (totalMemoryGb * 1024f) * 100f) else 0f
    
    // Icon Container: 与全站统一配色
    val containerColor = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    Text(
        text = stringResource(R.string.device_memory),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    SettingsItem(isFirst = true, isLast = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = containerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fair_memory),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_used_memory, String.format("%.1f", appMemoryMb)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.system_total_memory, String.format("%.1f", totalMemoryGb)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "${String.format("%.1f", usagePercent)}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DocumentationLinkCard(
    title: String,
    description: String,
    url: String,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    val context = LocalContext.current
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(28.dp)
        isFirst -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        isLast -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(0.dp)
    }
    Card(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // 无可用浏览器，忽略
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
