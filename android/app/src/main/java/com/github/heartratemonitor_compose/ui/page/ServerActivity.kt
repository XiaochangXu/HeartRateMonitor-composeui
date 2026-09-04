package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.server.ServerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ServerActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            ServerScreen(onNavigateBack = { finish() })
        }
    }
}