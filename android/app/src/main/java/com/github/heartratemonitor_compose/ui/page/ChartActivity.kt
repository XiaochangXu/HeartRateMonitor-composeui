package com.github.heartratemonitor_compose.ui.page

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.addCallback
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
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
        // ⚠️ 反直觉设计：竖屏时必须保持 callback 禁用，否则系统视为拦截返回，预测性返回动画不播放
        val rotateBack = onBackPressedDispatcher.addCallback(this, enabled = false) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setPageContent {
            // configChanges 下旋转不重建 Activity，须随 Configuration 切换拦截开关
            val landscape = LocalConfiguration.current.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            SideEffect { rotateBack.isEnabled = landscape }
            ChartScreen(sessionId = sessionId, onNavigateBack = { finish() })
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
