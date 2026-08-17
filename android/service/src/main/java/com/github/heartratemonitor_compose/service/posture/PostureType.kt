package com.github.heartratemonitor_compose.service.posture

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.heartratemonitor_compose.service.R

/**
 * - SITTING / STANDING：静止状态，触发心率预警检测
 * - EXERCISE：高心率属正常，不触发预警
 * - UNKNOWN：数据不足、未校准或非校准姿态，不触发预警
 */
enum class PostureType(@param:DrawableRes val iconRes: Int, @param:StringRes val labelRes: Int) {
    UNKNOWN(R.drawable.ic_posture_unknown, R.string.posture_unknown),
    SITTING(R.drawable.ic_posture_sitting, R.string.sitting),
    STANDING(R.drawable.ic_posture_standing, R.string.standing),
    EXERCISE(R.drawable.ic_posture_exercise, R.string.exercise);

    val isStationary: Boolean get() = this == SITTING || this == STANDING
}