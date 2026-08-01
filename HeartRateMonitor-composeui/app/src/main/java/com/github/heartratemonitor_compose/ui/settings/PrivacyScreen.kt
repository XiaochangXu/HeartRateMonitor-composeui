package com.github.heartratemonitor_compose.ui.settings

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R

/**
 * 隐私政策页面：罗列本应用会访问的数据以及如何使用这些数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.privacy_title),
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

            // 引言：说明应用隐私保护原则
            PrivacyIntroCard()

            // 数据访问详情：分段式卡片组
            SettingsGroupCard {
                PrivacyItem(
                    isFirst = true,
                    iconRes = R.drawable.ic_bluetooth_connected,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_bluetooth_title),
                    desc = stringResource(R.string.privacy_bluetooth_desc)
                )
                PrivacyItem(
                    iconRes = R.drawable.ic_heart,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_heart_rate_title),
                    desc = stringResource(R.string.privacy_heart_rate_desc)
                )
                PrivacyItem(
                    iconRes = R.drawable.ic_signal_wifi,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_location_title),
                    desc = stringResource(R.string.privacy_location_desc)
                )
                PrivacyItem(
                    iconRes = R.drawable.ic_deployed_code_history,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_storage_title),
                    desc = stringResource(R.string.privacy_storage_desc)
                )
                PrivacyItem(
                    iconRes = R.drawable.ic_warning,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_notification_title),
                    desc = stringResource(R.string.privacy_notification_desc)
                )
                PrivacyItem(
                    iconRes = R.drawable.ic_http_websocket,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_network_title),
                    desc = stringResource(R.string.privacy_network_desc)
                )
                PrivacyItem(
                    iconRes = R.drawable.ic_heart_icon,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_foreground_service_title),
                    desc = stringResource(R.string.privacy_foreground_service_desc)
                )
                PrivacyItem(
                    isLast = true,
                    iconRes = R.drawable.ic_version,
                    iconContainerColor = primaryContainer,
                    iconTint = iconTint,
                    title = stringResource(R.string.privacy_clipboard_title),
                    desc = stringResource(R.string.privacy_clipboard_desc)
                )
            }

            // 数据安全说明卡片
            PrivacyConclusionCard()

            // 内容延伸到屏幕底部，末尾留出胶囊+系统导航栏空间
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

@Composable
private fun PrivacyIntroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Text(
            text = stringResource(R.string.privacy_intro),
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacyItem(
    isFirst: Boolean = false,
    isLast: Boolean = false,
    iconRes: Int,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    desc: String
) {
    SettingsItem(isFirst = isFirst, isLast = isLast) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrivacyLeadingIcon(
                painter = painterResource(iconRes),
                containerColor = iconContainerColor,
                tint = iconTint
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivacyLeadingIcon(
    painter: Painter,
    containerColor: Color,
    tint: Color
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        }
    }
    Spacer(Modifier.width(16.dp))
}

@Composable
private fun PrivacyConclusionCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.privacy_conclusion_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.privacy_conclusion_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
