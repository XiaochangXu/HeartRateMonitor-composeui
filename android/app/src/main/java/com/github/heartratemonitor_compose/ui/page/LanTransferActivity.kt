package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.server.LanTransferScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LanTransferActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            LanTransferScreen(onNavigateBack = { finish() })
        }
    }
}