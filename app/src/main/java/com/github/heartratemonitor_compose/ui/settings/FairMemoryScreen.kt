package com.github.heartratemonitor_compose.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                        "公平运行内存",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
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

            // ── 概述 ──
            Text(
                text = "概述",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "公平运行内存机制是由金标联盟理事长成员（vivo、小米、OPPO、荣耀）联合倡导的安卓内存管理规范。该机制旨在通过标准化的系统广播，让应用在内存紧张时主动配合系统进行资源释放，从而提升设备整体运行体验与稳定性，减少后台应用被强制杀死的概率。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── 工作原理 ──
            Text(
                text = "工作原理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "当系统内存资源紧张时，设备 ROM 会向已注册的应用发送特定的广播通知。应用接收到广播后，可以根据广播类型执行相应的内存优化操作，包括释放非关键缓存、停止低优先级任务、保存当前状态等。这种主动配合的方式比系统直接杀死进程更加平滑，能够有效降低应用重启带来的体验中断。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── 监听的广播 ──
            Text(
                text = "监听的广播 Action",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. 内存预警",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Action: itgsa.intent.action.TRIM\n系统内存紧张时发送。应用收到后应主动释放非关键内存，如清理图片缓存、停止非必要的后台任务、触发垃圾回收等。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Text(
                        text = "2. 应用查杀",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Action: itgsa.intent.action.KILL\n系统即将查杀本应用时发送。应用收到后应保存关键状态数据、释放所有可释放的资源，为进程终止做好准备。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 本应用的适配 ──
            Text(
                text = "本应用的适配",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "本应用已按照金标联盟官方文档完成公平运行内存机制适配：\n\n• 在 Application 启动时动态注册广播接收器，使用 RECEIVER_EXPORTED 标志接收系统广播\n• 收到广播后解析 Intent 中的 common 和 extra 两个 Bundle，提取异常类型（物理内存/Java堆内存）、内存用量、回调 IBinder 等信息\n• TRIM 广播：按 notifyType 差异化释放——物理内存异常(1000)清空整个图表缓存，Java堆异常(2000)降采样保留最近 500 点；均清空扫描结果并触发 System.gc()，在 3 秒内通过 IBinder 回调通知系统处理结果\n• KILL 广播：通知 MainViewModel 保存状态（心率数据已通过 Room 数据库持久化），在 3 秒内通过 IBinder 回调通知系统\n• 通过 MemoryListener 接口（WeakReference 持有）将释放逻辑委托给 MainViewModel，避免内存泄漏\n• 实现 IBinder.DeathRecipient 监听系统服务异常死亡，确保 Binder 连接可靠\n• 使用独立 HandlerThread 处理广播，避免阻塞主线程\n\n蓝牙心率服务（BleService）作为前台服务持续运行，不会因内存预警而被中断。状态栏常驻服务和悬浮窗服务在内存极端紧张时可能被系统回收，应用会在下次启动时自动恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── 参考文档 ──
            Text(
                text = "参考文档",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DocumentationLinkCard(
                title = "OPPO",
                description = "公平运行内存适配指南",
                url = "https://open.oppomobile.com/documentation/page/info?id=13825"
            )
            DocumentationLinkCard(
                title = "vivo",
                description = "公平运行内存机制适配",
                url = "https://dev.vivo.com.cn/wap/documentCenter/doc/1013"
            )
            DocumentationLinkCard(
                title = "小米",
                description = "公平运行内存适配：开发者文档",
                url = "https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2304"
            )
            DocumentationLinkCard(
                title = "Android 17 Beta",
                description = "The Fourth Beta of Android 17",
                url = "https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html?m=1"
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * 参考文档卡片。点击跳转到对应链接。
 *
 * 使用 Material3 [Card] 的 onClick 重载，确保 ripple 裁剪到圆角
 * （符合项目规范：有点击交互的 Card 必须用 onClick 重载）。
 */
@Composable
private fun DocumentationLinkCard(
    title: String,
    description: String,
    url: String
) {
    val context = LocalContext.current
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
