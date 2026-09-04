package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.theme.ThemeSettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ThemeSettingsActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            ThemeSettingsScreen(onNavigateBack = { finish() })
        }
    }
}