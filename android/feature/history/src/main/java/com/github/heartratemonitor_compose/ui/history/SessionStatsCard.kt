package com.github.heartratemonitor_compose.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.history.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 会话统计摘要（均值/极值/时间范围） */
internal data class ChartStats(
    val avg: Int,
    val min: Int,
    val max: Int,
    val startTime: Long,
    val endTime: Long
)

/** 会话统计摘要卡片 */
@Composable
internal fun SessionStatsCard(
    stats: ChartStats,
    modifier: Modifier = Modifier
) {
    val compactTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val startTimeStr = remember(stats.startTime, compactTimeFormat) {
        compactTimeFormat.format(Date(stats.startTime))
    }
    val endTimeStr = remember(stats.endTime, compactTimeFormat) {
        compactTimeFormat.format(Date(stats.endTime))
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCell(
                    label = stringResource(R.string.stat_avg),
                    // 数值以 String 传入（%1$s），规避小语种 locale 整数格式化输出本地数字
                    value = stringResource(R.string.stat_bpm_value, stats.avg.toString()),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.stat_min),
                    value = stringResource(R.string.stat_bpm_value, stats.min.toString()),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCell(
                    label = stringResource(R.string.stat_max),
                    value = stringResource(R.string.stat_bpm_value, stats.max.toString()),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.stat_time),
                    value = stringResource(R.string.stat_time_range, startTimeStr, endTimeStr),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
