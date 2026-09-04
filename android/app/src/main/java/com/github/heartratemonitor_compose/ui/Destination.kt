package com.github.heartratemonitor_compose.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.github.heartratemonitor_compose.ui.page.AboutDetailsActivity
import com.github.heartratemonitor_compose.ui.page.AlarmActivity
import com.github.heartratemonitor_compose.ui.page.ChartActivity
import com.github.heartratemonitor_compose.ui.page.DevicesActivity
import com.github.heartratemonitor_compose.ui.page.FairMemoryActivity
import com.github.heartratemonitor_compose.ui.page.FloatingWindowSettingsActivity
import com.github.heartratemonitor_compose.ui.page.FullscreenActivity
import com.github.heartratemonitor_compose.ui.page.FullscreenSoundActivity
import com.github.heartratemonitor_compose.ui.page.FunctionSettingsActivity
import com.github.heartratemonitor_compose.ui.page.LanTransferActivity
import com.github.heartratemonitor_compose.ui.page.LanguageSettingsActivity
import com.github.heartratemonitor_compose.ui.page.LicenseActivity
import com.github.heartratemonitor_compose.ui.page.NavStyleActivity
import com.github.heartratemonitor_compose.ui.page.PrivacyActivity
import com.github.heartratemonitor_compose.ui.page.ServerActivity
import com.github.heartratemonitor_compose.ui.page.StatusBarSettingsActivity
import com.github.heartratemonitor_compose.ui.page.ThemeSettingsActivity
import com.github.heartratemonitor_compose.ui.page.WebhookActivity

/** 路由目标：route 字符串到 Activity 的映射，feature 模块靠 route 与 app 解耦。 */
internal sealed interface Destination {
    val key: String

    fun toIntent(context: Context): Intent

    data object License : Destination {
        override val key: String get() = Screen.License.route
        override fun toIntent(context: Context) = Intent(context, LicenseActivity::class.java)
    }

    data object Devices : Destination {
        override val key: String get() = Screen.Devices.route
        override fun toIntent(context: Context) = Intent(context, DevicesActivity::class.java)
    }

    data class Chart(val sessionId: Long) : Destination {
        override val key: String get() = Screen.Chart.createRoute(sessionId)
        override fun toIntent(context: Context) =
            Intent(context, ChartActivity::class.java).putExtra(ChartActivity.EXTRA_SESSION_ID, sessionId)
    }

    data object Alarm : Destination {
        override val key: String get() = Screen.Alarm.route
        override fun toIntent(context: Context) = Intent(context, AlarmActivity::class.java)
    }

    data object FunctionSettings : Destination {
        override val key: String get() = Screen.FunctionSettings.route
        override fun toIntent(context: Context) = Intent(context, FunctionSettingsActivity::class.java)
    }

    data object Theme : Destination {
        override val key: String get() = Screen.Theme.route
        override fun toIntent(context: Context) = Intent(context, ThemeSettingsActivity::class.java)
    }

    data object Language : Destination {
        override val key: String get() = Screen.Language.route
        override fun toIntent(context: Context) = Intent(context, LanguageSettingsActivity::class.java)
    }

    data object NavStyle : Destination {
        override val key: String get() = Screen.NavStyle.route
        override fun toIntent(context: Context) = Intent(context, NavStyleActivity::class.java)
    }

    data object FullscreenSound : Destination {
        override val key: String get() = Screen.FullscreenSound.route
        override fun toIntent(context: Context) = Intent(context, FullscreenSoundActivity::class.java)
    }

    data object Server : Destination {
        override val key: String get() = Screen.Server.route
        override fun toIntent(context: Context) = Intent(context, ServerActivity::class.java)
    }

    data object Webhook : Destination {
        override val key: String get() = Screen.Webhook.route
        override fun toIntent(context: Context) = Intent(context, WebhookActivity::class.java)
    }

    data object LanTransfer : Destination {
        override val key: String get() = Screen.LanTransfer.route
        override fun toIntent(context: Context) = Intent(context, LanTransferActivity::class.java)
    }

    data object StatusBarSettings : Destination {
        override val key: String get() = Screen.StatusBarSettings.route
        override fun toIntent(context: Context) = Intent(context, StatusBarSettingsActivity::class.java)
    }

    data object FloatingWindowSettings : Destination {
        override val key: String get() = Screen.FloatingWindowSettings.route
        override fun toIntent(context: Context) = Intent(context, FloatingWindowSettingsActivity::class.java)
    }

    data object AboutDetails : Destination {
        override val key: String get() = Screen.AboutDetails.route
        override fun toIntent(context: Context) = Intent(context, AboutDetailsActivity::class.java)
    }

    data object Privacy : Destination {
        override val key: String get() = Screen.Privacy.route
        override fun toIntent(context: Context) = Intent(context, PrivacyActivity::class.java)
    }

    data object FairMemory : Destination {
        override val key: String get() = Screen.FairMemory.route
        override fun toIntent(context: Context) = Intent(context, FairMemoryActivity::class.java)
    }

    data object Fullscreen : Destination {
        override val key: String get() = "fullscreen"
        override fun toIntent(context: Context) = Intent(context, FullscreenActivity::class.java)
    }

    companion object {
        fun of(route: String): Destination? = when (route) {
            Screen.License.route -> License
            Screen.Devices.route -> Devices
            Screen.Alarm.route -> Alarm
            Screen.FunctionSettings.route -> FunctionSettings
            Screen.Theme.route -> Theme
            Screen.Language.route -> Language
            Screen.NavStyle.route -> NavStyle
            Screen.FullscreenSound.route -> FullscreenSound
            Screen.Server.route -> Server
            Screen.Webhook.route -> Webhook
            Screen.LanTransfer.route -> LanTransfer
            Screen.StatusBarSettings.route -> StatusBarSettings
            Screen.FloatingWindowSettings.route -> FloatingWindowSettings
            Screen.AboutDetails.route -> AboutDetails
            Screen.Privacy.route -> Privacy
            Screen.FairMemory.route -> FairMemory
            else -> null
        }
    }
}

/** 同路由双击保护窗口：300ms 对齐 ViewConfiguration.getDoubleTapTimeout()。 */
internal const val SAME_ROUTE_DEBOUNCE_MS = 300L

// ⚠️ 反直觉设计：单调时钟，避免改时间/NTP 校时让防抖窗口失效
private var lastNavTimeMs = 0L
private var lastNavKey: String? = null

internal fun Context.launchDestination(destination: Destination) {
    val now = SystemClock.elapsedRealtime()
    if (destination.key == lastNavKey && now - lastNavTimeMs < SAME_ROUTE_DEBOUNCE_MS) {
        Log.w("Destination", "navigate blocked by same-route guard: ${destination.key}")
        return
    }
    lastNavTimeMs = now
    lastNavKey = destination.key
    // 不传 ActivityOptions：使用系统默认窗口转场（决策：100% 交给系统，不自定义动画）
    startActivity(destination.toIntent(this))
}
