package com.github.heartratemonitor_compose.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController

/**
 * 防止两种竞态场景：
 * 1. 同路由双击：短窗口内重复压栈
 * 2. 异路由转场：动画期间新导航导致 AnimatedContent 状态不同步
 */
class NavGuard {
    var lastNavTimeMs = 0L
    var lastRoute: String? = null
}

@Composable
fun rememberNavGuard(): NavGuard = remember { NavGuard() }

@Composable
fun rememberSafeNavigate(navController: NavController, navGuard: NavGuard): (String) -> Unit =
    remember(navController, navGuard) {
        nav@{ route: String ->
            val now = System.currentTimeMillis()
            val elapsed = now - navGuard.lastNavTimeMs
            // 同路由短窗口：防双击重复压栈
            if (route == navGuard.lastRoute && elapsed < SAME_ROUTE_DEBOUNCE_MS) {
                Log.w("AppRoot", "navigate blocked by same-route guard: $route, ${elapsed}ms")
                return@nav
            }
            // 异路由转场互斥：防 AnimatedContent 竞态
            if (elapsed < TRANSITION_DEBOUNCE_MS) {
                Log.w("AppRoot", "navigate blocked by transition debounce: $route, ${elapsed}ms since last")
                return@nav
            }
            navGuard.lastNavTimeMs = now
            navGuard.lastRoute = route
            Log.d("AppRoot", "navigate: $route, from=${navController.currentDestination?.route}")
            navController.navigate(route)
        }
    }

@Composable
fun rememberSafePopBack(navController: NavController, navGuard: NavGuard): () -> Unit =
    remember(navController, navGuard) {
        pop@{
            val now = System.currentTimeMillis()
            if (now - navGuard.lastNavTimeMs < TRANSITION_DEBOUNCE_MS) {
                Log.w("AppRoot", "popBack blocked by debounce: ${now - navGuard.lastNavTimeMs}ms since last")
                return@pop
            }
            navGuard.lastNavTimeMs = now
            val result = navController.popBackStack()
            Log.d("AppRoot", "popBack: result=$result, currentRoute=${navController.currentDestination?.route}")
            if (!result) {
                Log.w("AppRoot", "popBack failed, navigating to placeholder")
                navController.navigate(TAB_PLACEHOLDER) {
                    popUpTo(TAB_PLACEHOLDER) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }
