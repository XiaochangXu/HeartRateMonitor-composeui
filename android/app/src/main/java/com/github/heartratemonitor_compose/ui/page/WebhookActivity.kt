package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WebhookActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            WebhookScreen(onNavigateBack = { finish() })
        }
    }
}