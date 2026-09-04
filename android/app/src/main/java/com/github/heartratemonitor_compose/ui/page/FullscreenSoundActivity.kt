package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.settings.FullscreenSoundScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FullscreenSoundActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            FullscreenSoundScreen(onNavigateBack = { finish() })
        }
    }
}