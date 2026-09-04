package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.alarm.HeartRateAlarmScreen
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            HeartRateAlarmScreen(onNavigateBack = { finish() })
        }
    }
}
