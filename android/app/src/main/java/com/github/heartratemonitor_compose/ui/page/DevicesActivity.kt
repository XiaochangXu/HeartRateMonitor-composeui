package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.main.DevicesScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DevicesActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            // 独立实例：数据面经 HeartRateRepository 单例一致，控制面经 BleControlPlaneRegistry 共享
            DevicesScreen(viewModel = hiltViewModel(), onNavigateBack = { finish() })
        }
    }
}
