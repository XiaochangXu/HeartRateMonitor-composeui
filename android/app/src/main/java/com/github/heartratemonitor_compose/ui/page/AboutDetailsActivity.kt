package com.github.heartratemonitor_compose.ui.page

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.heartratemonitor_compose.ui.Destination
import com.github.heartratemonitor_compose.ui.base.BaseComposeActivity
import com.github.heartratemonitor_compose.ui.launchDestination
import com.github.heartratemonitor_compose.ui.settings.AboutDetailsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutDetailsActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageContent {
            val context = LocalContext.current
            AboutDetailsScreen(
                onNavigate = remember(context) {
                    { route: String -> Destination.of(route)?.let { context.launchDestination(it) } }
                },
                onNavigateBack = { finish() },
                onOpenExternal = remember(this) {
                    { intent: Intent -> openExternal(intent) }
                },
                showToast = remember(context) {
                    { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                }
            )
        }
    }
}
