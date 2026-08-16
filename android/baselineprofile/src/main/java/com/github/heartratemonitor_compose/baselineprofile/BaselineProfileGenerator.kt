package com.github.heartratemonitor_compose.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器。
 *
 * 定义关键用户旅程（CUJ），由 Macrobenchmark 自动化执行，
 * ART 采集热点代码后生成 baseline-prof.txt / baseline-prof-startup.txt。
 *
 * 运行方式（需连接真机）：
 *   ./gradlew :baselineprofile:connectedDebugAndroidTest
 *
 * 生成产物路径：
 *   baselineprofile/build/outputs/managed_device_benchmark_profiles/
 *   或 adb pull /data/local/tmp/baseline-prof-*
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val targetPackage = "com.github.heartratemonitor_compose"

    /**
     * 采集冷启动 + Tab 导航 + 二级页面跳转的基线剖析。
     *
     * collect() 是 1.3+ 标准化 API，专门采集基线剖析文件，
     * 与 MacrobenchmarkRule.measureRepeated() 性能测速区分开。
     */
    @Test
    fun generate() = baselineProfileRule.collect(packageName = targetPackage) {
        // ── 1. 冷启动 ──
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // ── 2. 处理首次安装可能出现的对话框 ──
        dismissDialogs()

        // ── 3. 等待首页渲染完成（中英文标题均支持） ──
        waitForText("Heart Rate Monitor", "心率监控器", timeoutMs = 5_000)
        device.waitForIdle()

        // ── 4. 导航到历史 Tab ──
        findAndClick("History", "历史")
        device.waitForIdle()

        // ── 5. 导航到收藏 Tab ──
        findAndClick("Favorites", "收藏")
        device.waitForIdle()

        // ── 6. 导航到设置 Tab ──
        findAndClick("Settings", "设置")
        device.waitForIdle()

        // ── 7. 进入「关于」二级页面（NavHost 转场动画） ──
        findAndClick("About", "关于")
        device.waitForIdle()

        // ── 8. 返回设置页 ──
        device.pressBack()
        device.waitForIdle()
    }

    /**
     * 逐轮检测并关闭权限请求对话框和更新日志 BottomSheet。
     *
     * 首次安装时可能出现：
     * - PermissionX 解释对话框（按钮 "OK" / "确认"）
     * - 系统权限对话框（按钮 "While using the app" / "Allow" / "仅在使用时允许" / "允许"）
     * - 更新日志 BottomSheet（按钮 "OK" / "确认"）
     */
    private fun MacrobenchmarkScope.dismissDialogs() {
        repeat(5) {
            device.waitForIdle()

            // PermissionX / 更新日志 的确认按钮
            val confirm = device.findObject(By.text("OK"))
                ?: device.findObject(By.text("确认"))
            if (confirm != null) {
                confirm.click()
                Thread.sleep(500)
                return@repeat
            }

            // 系统权限对话框
            val allowTexts = listOf(
                "While using the app", "Allow", "Only this time",
                "仅在使用时允许", "允许", "仅这次允许"
            )
            for (text in allowTexts) {
                val button = device.findObject(By.text(text))
                if (button != null) {
                    button.click()
                    Thread.sleep(500)
                    return@repeat
                }
            }

            // 未检测到对话框，退出循环
            return
        }
    }

    /**
     * 按文本查找并点击元素，支持中英文。
     */
    private fun MacrobenchmarkScope.findAndClick(vararg texts: String) {
        for (text in texts) {
            val obj = device.findObject(By.text(text))
            if (obj != null) {
                obj.click()
                return
            }
        }
    }

    /**
     * 等待任一文本出现（中英文兼容），超时后继续。
     */
    private fun MacrobenchmarkScope.waitForText(
        vararg texts: String,
        timeoutMs: Long
    ) {
        for (text in texts) {
            if (device.wait(Until.hasObject(By.text(text)), timeoutMs)) {
                return
            }
        }
    }
}
