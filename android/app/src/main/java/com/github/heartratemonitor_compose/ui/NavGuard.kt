package com.github.heartratemonitor_compose.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** 同路由双击保护窗口：短窗口内对相同键重复压栈直接忽略。 */
const val SAME_ROUTE_DEBOUNCE_MS = 100L

class NavGuard {
    var lastNavTimeMs = 0L
    var lastKey: Any? = null
}

@Composable
fun rememberNavGuard(): NavGuard = remember { NavGuard() }

@Composable
fun rememberSafeNavigate(navBackStack: NavBackStack<NavKey>, navGuard: NavGuard): (AppNavKey) -> Unit =
    remember(navBackStack, navGuard) {
        nav@{ key: AppNavKey ->
            val now = System.currentTimeMillis()
            // 同路由短窗口：防双击重复压栈
            if (key == navGuard.lastKey && now - navGuard.lastNavTimeMs < SAME_ROUTE_DEBOUNCE_MS) {
                Log.w("AppRoot", "navigate blocked by same-route guard: $key")
                return@nav
            }
            navGuard.lastNavTimeMs = now
            navGuard.lastKey = key
            Log.d("AppRoot", "navigate: $key")
            navBackStack.add(key)
        }
    }

@Composable
fun rememberSafePopBack(navBackStack: NavBackStack<NavKey>): () -> Unit =
    remember(navBackStack) {
        {
            // 栈底 TabRoot 占位不可弹出
            if (navBackStack.size > 1) {
                navBackStack.removeAt(navBackStack.lastIndex)
            } else {
                Log.w("AppRoot", "popBack on root stack ignored")
            }
        }
    }
