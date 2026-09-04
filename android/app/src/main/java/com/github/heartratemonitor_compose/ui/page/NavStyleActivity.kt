package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.settings.NavStyleScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NavStyleActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            NavStyleScreen(onNavigateBack = { finish() })
        }
    }
}