package com.github.heartratemonitor_compose.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

// 构造时同步预热内存快照，此后同步读零 IO；写异步落盘 + 乐观同步快照保持「写后立读」。
class SettingsRepository(context: Context, private val scope: CoroutineScope) {

    private val dataStore = context.applicationContext.settingsDataStore

    private val prefsState = MutableStateFlow(runBlocking { dataStore.data.first() })

    private val _settings = MutableStateFlow(AppSettings.from(prefsState.value))

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val flows = ConcurrentHashMap<Preferences.Key<*>, MutableStateFlow<Any?>>()
    private val nullableStringFlows =
        ConcurrentHashMap<Preferences.Key<*>, MutableStateFlow<String?>>()

    init {
        // DataStore 变更驱动：对账内存快照与各 observe 缓存（替代旧 OnSharedPreferenceChangeListener，
        // 不再需要 @Volatile listener 防 R8 inline 的 hack）。
        scope.launch {
            dataStore.data.collect { prefs ->
                prefsState.value = prefs
                _settings.value = AppSettings.from(prefs)
                @Suppress("UNCHECKED_CAST")
                flows.forEach { (key, flow) ->
                    // key 缺失时保留旧值（与原字符串键时代语义一致）
                    flow.value = prefs[key as Preferences.Key<Any>] ?: flow.value
                }
                // nullable 字符串：key 缺失（被删除）时必须更新为 null，不能保留旧值
                nullableStringFlows.forEach { (key, flow) ->
                    @Suppress("UNCHECKED_CAST")
                    flow.value = prefs[key as Preferences.Key<String>]
                }
            }
        }
    }

    // CAS 循环保证原子性：多线程（UI / Service / MemoryDiagnostics）并发写不同 key 时非原子读改写会丢先写者的键。
    private inline fun updateCache(transform: (MutablePreferences) -> Unit) {
        prefsState.update { current ->
            current.toMutablePreferences().also(transform)
                .also { _settings.value = AppSettings.from(it) }
        }
    }

    fun <T> get(key: Preferences.Key<T>): T = get(key, AppSettings.defaultFor(key))

    // 仅限历史默认值分歧点使用，新增调用点一律使用无默认值参数的 get。
    fun <T> get(key: Preferences.Key<T>, default: T): T =
        prefsState.value[key] ?: default

    fun <T> set(key: Preferences.Key<T>, value: T) {
        updateCache { it[key] = value }
        flows[key]?.value = value
        nullableStringFlows[key]?.let {
            @Suppress("UNCHECKED_CAST")
            it.value = value as String?
        }
        scope.launch {
            try {
                dataStore.edit { it[key] = value }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ⚠️ 反直觉设计：磁盘写失败不得炸进程；内存已乐观更新，磁盘旧值重启后回退。
                Log.e(TAG, "设置落盘失败: $key", e)
            }
        }
    }

    fun <T> observe(key: Preferences.Key<T>): StateFlow<T> =
        observe(key, AppSettings.defaultFor(key))

    @Suppress("UNCHECKED_CAST")
    fun <T> observe(key: Preferences.Key<T>, default: T): StateFlow<T> =
        flows.computeIfAbsent(key) { MutableStateFlow(get(key, default)) } as StateFlow<T>

    // 允许缺失的 key 缺失时返回 null。
    fun getNullable(key: Preferences.Key<String>): String? = prefsState.value[key]

    fun observeNullable(key: Preferences.Key<String>): StateFlow<String?> {
        @Suppress("UNCHECKED_CAST")
        return nullableStringFlows.computeIfAbsent(key) {
            // 同步登记进主缓存：set/remove 的乐观更新与 DataStore 对账只遍历 flows，
            // 未登记会导致仅有 nullable 观察者的 key 收不到更新
            flows.computeIfAbsent(key) {
                MutableStateFlow(prefsState.value[key])
            } as MutableStateFlow<String?>
        }
    }

    // 类型化键写入类型唯一，删除自身即可。
    fun remove(key: Preferences.Key<*>) {
        updateCache { it.remove(key) }
        nullableStringFlows[key]?.value = null
        scope.launch {
            try {
                dataStore.edit { it.remove(key) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "设置删除落盘失败: $key", e)
            }
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"
    }
}
