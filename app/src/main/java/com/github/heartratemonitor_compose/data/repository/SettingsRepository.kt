package com.github.heartratemonitor_compose.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.github.heartratemonitor_compose.data.PrefsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用设置统一仓库。
 *
 * - 封装唯一 `SharedPreferences` 实例（[PrefsKeys.FILE_NAME]），避免 UI 层直接访问数据层。
 * - 提供同步读写方法，供一次性读取/写入使用。
 * - 为每个 key 缓存独立的 [StateFlow]，通过 [OnSharedPreferenceChangeListener] 监听变化，
 *   多个收集者共享同一实例。
 * - 使用 [Context.getApplicationContext]，不持有 Activity/Service 上下文，防止内存泄漏。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PrefsKeys.FILE_NAME,
        Context.MODE_PRIVATE
    )

    private val booleanFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val stringFlows = ConcurrentHashMap<String, MutableStateFlow<String>>()
    private val nullableStringFlows = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val intFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()
    private val floatFlows = ConcurrentHashMap<String, MutableStateFlow<Float>>()
    private val longFlows = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        booleanFlows[key]?.let { it.value = getBoolean(key, it.value) }
        stringFlows[key]?.let { it.value = getString(key, it.value) }
        // nullableStringFlows 的 default 必须用 null，不能用 it.value（旧值）。
        // 当 putString(key, null) → remove(key) 时，prefs.getString(key, it.value) 会返回 it.value（旧值），
        // 导致 StateFlow 永远不会更新到 null。改用 null 作为 default，key 被移除时正确返回 null。
        nullableStringFlows[key]?.let { it.value = prefs.getString(key, null) }
        intFlows[key]?.let { it.value = getInt(key, it.value) }
        floatFlows[key]?.let { it.value = getFloat(key, it.value) }
        longFlows[key]?.let { it.value = getLong(key, it.value) }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    // ── Boolean ──
    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun observeBoolean(key: String, default: Boolean): StateFlow<Boolean> {
        return booleanFlows.computeIfAbsent(key) { MutableStateFlow(getBoolean(key, default)) }
    }

    // ── String ──
    fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun observeString(key: String, default: String): StateFlow<String> {
        return stringFlows.computeIfAbsent(key) { MutableStateFlow(getString(key, default)) }
    }

    /**
     * 读取可能为 null 的字符串。用于 [PrefsKeys.FAVORITE_DEVICE_ID] 等允许缺失的 key。
     */
    fun getStringNullable(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun observeStringNullable(key: String, default: String? = null): StateFlow<String?> {
        return nullableStringFlows.computeIfAbsent(key) {
            MutableStateFlow(getStringNullable(key, default))
        }
    }

    // ── Int ──
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun observeInt(key: String, default: Int): StateFlow<Int> {
        return intFlows.computeIfAbsent(key) { MutableStateFlow(getInt(key, default)) }
    }

    // ── Float ──
    fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)

    fun setFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun observeFloat(key: String, default: Float): StateFlow<Float> {
        return floatFlows.computeIfAbsent(key) { MutableStateFlow(getFloat(key, default)) }
    }

    // ── Long ──
    fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun observeLong(key: String, default: Long): StateFlow<Long> {
        return longFlows.computeIfAbsent(key) { MutableStateFlow(getLong(key, default)) }
    }

    /**
     * 删除指定 key。用于姿态校准数据清空等场景。
     */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
