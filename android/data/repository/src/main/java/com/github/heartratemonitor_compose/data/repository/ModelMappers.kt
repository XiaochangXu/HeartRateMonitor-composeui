package com.github.heartratemonitor_compose.data.repository

import com.github.heartratemonitor_compose.data.db.FavoriteDeviceEntity
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import com.github.heartratemonitor_compose.data.db.SessionStats
import com.github.heartratemonitor_compose.data.model.FavoriteDeviceInfo
import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.data.model.HeartRateSessionInfo
import com.github.heartratemonitor_compose.data.model.SessionStatsInfo

/**
 * 依据契约 1（映射在 Repository 层完成）与多模块化 Phase 1（C7）：
 * 映射函数从 data.model 迁出，使 :core:model 模块不再依赖 data.db。
 */
fun FavoriteDeviceEntity.toInfo(): FavoriteDeviceInfo =
    FavoriteDeviceInfo(
        id = id,
        name = name,
        timestamp = timestamp
    )

fun FavoriteDeviceInfo.toEntity(): FavoriteDeviceEntity =
    FavoriteDeviceEntity(
        id = id,
        name = name,
        timestamp = timestamp
    )

fun HeartRateRecord.toInfo(): HeartRateRecordInfo =
    HeartRateRecordInfo(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        heartRate = heartRate
    )

fun HeartRateSession.toInfo(): HeartRateSessionInfo =
    HeartRateSessionInfo(
        id = id,
        deviceName = deviceName,
        startTime = startTime,
        endTime = endTime
    )

fun SessionStats.toInfo(): SessionStatsInfo =
    SessionStatsInfo(
        sessionId = sessionId,
        recordCount = recordCount,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        minHeartRate = minHeartRate,
        firstTimestamp = firstTimestamp,
        lastTimestamp = lastTimestamp
    )
