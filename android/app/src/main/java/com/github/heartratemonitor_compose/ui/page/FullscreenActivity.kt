package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 全屏心率页：横屏由 manifest 锁定，系统栏/常亮由 FullScreenHeartRate 内部管理。 */
@AndroidEntryPoint
class FullscreenActivity : BaseComposeActivity() {

    @Inject lateinit var killStateSaver: KillStateSaver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFullScreenFlag(true)
        setPageContent {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // BLE 断连自动退出（原 AppLifecycleEffects 职责随全屏页迁移）
            LaunchedEffect(uiState.appStatus) {
                if (uiState.appStatus != AppStatus.CONNECTED) finish()
            }
            FullScreenHeartRate(viewModel = viewModel, onExit = { finish() })
        }
    }

    override fun onDestroy() {
        setFullScreenFlag(false)
        super.onDestroy()
    }

    // ⚠️ 反直觉设计：copy 保留 MainActivity 侧 AppLifecycleEffects 持续写入的 tab/device 字段
    private fun setFullScreenFlag(active: Boolean) {
        killStateSaver.updateSnapshot(killStateSaver.currentSnapshot.copy(isFullScreen = active))
    }
}
