package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.settings.FloatingWindowSettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FloatingWindowSettingsActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            FloatingWindowSettingsScreen(onNavigateBack = { finish() })
        }
    }
}