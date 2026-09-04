package com.github.heartratemonitor_compose.ui.base

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.main.LocaleHelper
import com.github.heartratemonitor_compose.ui.main.MainActivity
import com.github.heartratemonitor_compose.ui.theme.AppTheme
import com.github.heartratemonitor_compose.ui.theme.CustomSchemeCache
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 二级页宿主公共配置：主题 / 语言 / edge-to-edge，子类须同时标注 @AndroidEntryPoint。 */
@AndroidEntryPoint
abstract class BaseComposeActivity : FragmentActivity() {

    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var customSchemeCache: CustomSchemeCache

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    protected fun setPageContent(content: @Composable () -> Unit) {
        setContent {
            AppTheme(themeState = themeState, customSchemeCache = customSchemeCache) {
                content()
            }
        }
    }

    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** 外链跳转 + 最近任务抑制窗口（suppress 标志维护在 MainActivity companion）。 */
    protected fun openExternal(intent: Intent) {
        try {
            startActivity(intent)
            MainActivity.setSuppressHideForExternalLaunch(true)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "外部链接跳转失败：无 Activity 可处理该 Intent", e)
            showToast(getString(R.string.toast_permissions_denied))
        } catch (e: Exception) {
            Log.e(TAG, "外部链接跳转失败", e)
        }
    }

    companion object {
        private const val TAG = "BaseComposeActivity"
    }
}
