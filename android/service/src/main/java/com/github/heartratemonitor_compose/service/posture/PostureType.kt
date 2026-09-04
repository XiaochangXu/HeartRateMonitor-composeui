package com.github.heartratemonitor_compose.service.posture

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.heartratemonitor_compose.service.R

/**
 * 静止姿态（SITTING/STANDING）触发心率预警；EXERCISE/UNKNOWN 不触发。
 */
enum class PostureType(@param:DrawableRes val iconRes: Int, @param:StringRes val labelRes: Int) {
    UNKNOWN(R.drawable.ic_posture_unknown, R.string.posture_unknown),
    SITTING(R.drawable.ic_posture_sitting, R.string.sitting),
    STANDING(R.drawable.ic_posture_standing, R.string.standing),
    EXERCISE(R.drawable.ic_posture_exercise, R.string.exercise);

    val isStationary: Boolean get() = this == SITTING || this == STANDING
}
