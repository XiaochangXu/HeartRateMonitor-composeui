package com.github.heartratemonitor_compose.ui.page

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.addCallback
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.history.ChartScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChartActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0L) {
            finish()
            return
        }
        // 系统返回键与顶部返回按钮行为对齐：横屏先转竖屏，再按一次才退出
        // （迁移前由 NavDisplay.onBack 承担该逻辑，独立 Activity 后须自行接管）
        onBackPressedDispatcher.addCallback(this) {
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                finish()
            }
        }
        setPageContent {
            ChartScreen(sessionId = sessionId, onNavigateBack = { finish() })
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
