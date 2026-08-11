package com.github.heartratemonitor_compose.ui.settings

import android.content.Context
import android.util.Log
import com.github.heartratemonitor_compose.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "UpdateChecker"

    private const val OWNER = "xiaochang-xu"
    private const val REPO = "heart-rate-monitor-composeui"
    private const val API_URL =
        "https://gitee.com/api/v5/repos/$OWNER/$REPO/releases?page=1&per_page=100"
    private const val RELEASE_PAGE_URL =
        "https://gitee.com/$OWNER/$REPO/releases/latest"

    /**
     * 检查结果密封类。
     */
    sealed class Result {
        /** 发现新版本。[newVersion] 已去除 'v' 前缀，[releaseNotes] 为 Release body（可能为空），[htmlUrl] 为 Release 页 URL */
        data class UpdateAvailable(
            val newVersion: String,
            val releaseNotes: String,
            val htmlUrl: String
        ) : Result()

        /** 当前已是最新版本。[currentVersion] 当前版本号，[releaseNotes] 为当前版本的 Release body，[htmlUrl] 为 Release 页 URL */
        data class UpToDate(
            val currentVersion: String,
            val releaseNotes: String,
            val htmlUrl: String
        ) : Result()

        /** 检查失败。[message] 错误描述（用于弹窗显示） */
        data class Error(val message: String) : Result()
    }

    /**
     * 异步检查最新版本。
     *
     * @param currentVersion 当前应用版本名（已去除 'v' 前缀，如 "1.0.0"）
     */
    suspend fun check(context: Context, currentVersion: String): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val startMs = System.currentTimeMillis()
        Log.i(TAG, "check: start, currentVersion=$currentVersion, url=$API_URL")
        try {
            val (code, body) = httpGet(API_URL)
            val elapsed = System.currentTimeMillis() - startMs
            Log.i(TAG, "check: http done in ${elapsed}ms, code=$code, bodyLen=${body.length}")
            when {
                code == 404 -> Result.Error(appContext.getString(R.string.update_no_release))
                code == 403 -> Result.Error(appContext.getString(R.string.update_api_rate_limit))
                code != 200 -> Result.Error(appContext.getString(R.string.update_api_error, code))
                else -> findLatestRelease(appContext, body, currentVersion)
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            Log.e(TAG, "check: failed in ${elapsed}ms", e)
            Result.Error(appContext.getString(R.string.update_network_error, e.message ?: e.javaClass.simpleName))
        }
    }

    /**
     * 从 Gitee releases 列表中找版本号最高的 release，与当前版本比较。
     *
     * 跳过 prerelease，取 tag_name 按语义化版本比较取最大值。
     */
    private fun findLatestRelease(context: Context, body: String, currentVersion: String): Result {
        val releases = JSONArray(body)
        if (releases.length() == 0) {
            return Result.Error(context.getString(R.string.update_no_release))
        }

        // 遍历所有 release，找出版本号最高的非 prerelease
        var bestVersion = ""
        var bestRelease: JSONObject? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("prerelease", false)) continue
            val tagName = release.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
            if (tagName.isEmpty()) continue
            if (bestVersion.isEmpty() || compareVersions(tagName, bestVersion) > 0) {
                bestVersion = tagName
                bestRelease = release
            }
        }

        if (bestRelease == null || bestVersion.isEmpty()) {
            return Result.Error(context.getString(R.string.update_no_valid_release))
        }

        val releaseNotes = bestRelease.optString("body", "").trim()
        val htmlUrl = bestRelease.optString("html_url", RELEASE_PAGE_URL).trim()

        val cmp = compareVersions(currentVersion, bestVersion)
        return if (cmp < 0) {
            Result.UpdateAvailable(bestVersion, releaseNotes, htmlUrl)
        } else {
            Result.UpToDate(currentVersion, releaseNotes, htmlUrl)
        }
    }

    /**
     * 发起 HTTP GET 请求，返回 (statusCode, body)。
     *
     * 连接 + 读取各 5 秒超时。
     */
    private fun httpGet(urlStr: String): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "HeartRateMonitor-Android-App")
        conn.instanceFollowRedirects = true
        try {
            val t0 = System.currentTimeMillis()
            conn.connect()
            val t1 = System.currentTimeMillis()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val t2 = System.currentTimeMillis()
            Log.i(TAG, "httpGet: connect=${t1 - t0}ms, responseCode=${t2 - t1}ms, total=${t2 - t0}ms, code=$code")
            return Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 语义化版本比较。
     *
     * 支持 "1.2.3" / "1.2" / "1" 等格式，不足的位补 0。
     * 不处理预发布后缀（-alpha/-beta 等），仅比较数字位。
     *
     * @return 负数 = current < remote（有更新）；0 = 相等；正数 = current > remote
     */
    private fun compareVersions(current: String, remote: String): Int {
        val cur = current.split(".").map { it.toIntOrNull() ?: 0 }
        val rem = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(cur.size, rem.size)
        for (i in 0 until maxLen) {
            val c = cur.getOrElse(i) { 0 }
            val r = rem.getOrElse(i) { 0 }
            if (c != r) return c - r
        }
        return 0
    }
}
