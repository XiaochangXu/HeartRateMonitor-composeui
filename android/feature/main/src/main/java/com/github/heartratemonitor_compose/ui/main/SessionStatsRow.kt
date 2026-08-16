package com.github.heartratemonitor_compose.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.main.R

/**
 * 首页统计卡片区：
 * 最大/最低心率双卡（仿 legado 首页累计阅读/阅读时长双卡布局）。
 */
@Composable
internal fun SessionStatsRow(
    sessionMaxHr: Int,
    sessionMinHr: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeartRateStatCard(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_heart_rate_max,
            title = stringResource(R.string.max_heart_rate),
            value = if (sessionMaxHr > 0) "$sessionMaxHr" else "--",
            unit = stringResource(R.string.bpm_unit)
        )
        HeartRateStatCard(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_heart_rate_min,
            title = stringResource(R.string.min_heart_rate),
            value = if (sessionMinHr > 0) "$sessionMinHr" else "--",
            unit = stringResource(R.string.bpm_unit)
        )
    }
}
