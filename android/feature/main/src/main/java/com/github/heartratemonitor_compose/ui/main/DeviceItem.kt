package com.github.heartratemonitor_compose.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.main.R
import com.github.heartratemonitor_compose.ui.theme.SignalMediumColor
import com.github.heartratemonitor_compose.ui.theme.SignalStrongColor
import com.github.heartratemonitor_compose.ui.theme.SignalWeakColor
import com.juul.kable.Advertisement

/**
 * 设备列表项：替代原 list_item_device.xml + DeviceAdapter。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceItem(
    advertisement: Advertisement,
    isFavorite: Boolean,
    isConnecting: Boolean,
    onDeviceClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val rssi = advertisement.rssi
    val strongColor = SignalStrongColor // primary_light
    val mediumColor = SignalMediumColor
    val weakColor = SignalWeakColor   // red_error

    // 信号强度统一使用 WiFi 信号图标，通过颜色区分强/中/弱
    val signalTint: ComposeColor
    val rssiColor: ComposeColor
    when {
        rssi > -65 -> {
            signalTint = strongColor
            rssiColor = strongColor
        }
        rssi > -80 -> {
            signalTint = mediumColor
            rssiColor = mediumColor
        }
        else -> {
            signalTint = weakColor
            rssiColor = weakColor
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable { onDeviceClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advertisement.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = advertisement.identifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isConnecting) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(40.dp)
            )
        } else {
            Icon(
                painter = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_signal_wifi),
                contentDescription = null,
                tint = signalTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${rssi}dBm",
                style = MaterialTheme.typography.labelSmall,
                color = rssiColor
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star
                               else Icons.Filled.StarBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
