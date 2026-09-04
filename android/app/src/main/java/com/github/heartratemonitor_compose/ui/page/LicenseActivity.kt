package com.github.heartratemonitor_compose.ui.page

import android.os.Bundle
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.settings.LicenseScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LicenseActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            LicenseScreen(onNavigateBack = { finish() })
        }
    }
}
