package com.github.heartratemonitor_compose.service.posture

/**
 * 姿态类型枚举。
 *
 * 每个姿态绑定一个 emoji 字符和一个中文标签，UI 直接引用避免硬编码散落。
 * - SITTING 静坐、STANDING 站立：静止状态，会触发心率预警检测
 * - EXERCISE 运动：高心率属正常，不触发预警
 * - UNKNOWN 未检测：数据不足、未校准或非校准姿态（如躺下睡眠），不触发预警
 */
enum class PostureType(val emoji: String, val label: String) {
    UNKNOWN("❓", "未检测"),
    SITTING("🧘", "静坐"),
    STANDING("🧍", "站立"),
    EXERCISE("🏃", "运动");

    /** 是否为静止状态（静坐或站立），用于预警状态机判断 */
    val isStationary: Boolean get() = this == SITTING || this == STANDING
}
