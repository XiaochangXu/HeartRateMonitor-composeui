package com.github.heartratemonitor_compose.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.lang.ref.WeakReference

/**
 * 公平运行内存机制适配器。
 *
 * 金标联盟（vivo、小米、OPPO、荣耀）定义的两个系统广播：
 * - [ACTION_TRIM]：系统内存紧张时通知应用主动释放非关键内存
 * - [ACTION_KILL]：系统即将查杀应用，通知应用保存状态并释放资源
 *
 * 实现要点（遵循官方文档）：
 * 1. 动态注册广播接收器，使用 [Context.RECEIVER_EXPORTED]（API 33+）
 * 2. 解析 Intent extras 中的 common / extra 两个 Bundle
 * 3. 提取 common 中的 callback IBinder
 * 4. 在 3 秒内通过 IBinder.transact() 回调处理结果
 * 5. 实现 [IBinder.DeathRecipient] 监听系统服务死亡
 * 6. 使用独立 HandlerThread 处理广播
 */
class FairMemoryReceiver private constructor() : IBinder.DeathRecipient {

    /**
     * 内存压力事件监听器。
     *
     * 由 ViewModel 或其他需要响应内存压力的组件实现，在收到 TRIM/KILL 广播时
     * 被 [FairMemoryReceiver] 调用。使用 [WeakReference] 持有，避免泄漏。
     *
     * 调用线程：[FairMemoryReceiver] 的 HandlerThread，实现方如需操作 UI 线程
     * 数据应自行切换线程（如 post 到主线程）。
     */
    interface MemoryListener {
        /**
         * 系统内存紧张，应用应主动释放非关键内存（清缓存、降采样等）。
         *
         * @param notifyType 异常类型，决定释放力度：
         * - [NOTIFY_TYPE_PSS]（1000）物理内存异常，更紧急，应最大化释放
         * - [NOTIFY_TYPE_HEAP]（2000）Java 堆内存异常，gc 直接有效
         */
        fun onTrimMemory(notifyType: Int)

        /** 系统即将查杀进程，应用应保存关键状态。 */
        fun onKillMemory()
    }

    companion object {
        private const val TAG = "FairMemory"

        const val ACTION_TRIM = "itgsa.intent.action.TRIM"
        const val ACTION_KILL = "itgsa.intent.action.KILL"

        /** Binder 回调 transaction code */
        private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION

        /** Intent extras keys */
        private const val BUNDLE_KEY_COMMON = "common"
        private const val BUNDLE_KEY_EXTRA = "extra"

        /** common bundle keys */
        private const val KEY_NOTIFY_TYPE = "notifyType"
        private const val KEY_NOTIFY_ID = "notifyId"
        private const val KEY_REASON = "reason"
        private const val KEY_ACTION = "action"
        private const val KEY_CALLBACK = "callback"

        /** extra bundle keys */
        private const val KEY_HEAP_ALLOC = "heapAlloc"
        private const val KEY_HEAP_CAPACITY = "heapCapacity"
        private const val KEY_PSS = "pss"
        private const val KEY_PSS_LIMIT = "pssLimit"

        /** 异常通知类型 */
        const val NOTIFY_TYPE_PSS = 1000   // 物理内存异常
        const val NOTIFY_TYPE_HEAP = 2000  // Java 堆内存异常

        /** 回调结果 */
        const val RESULT_SUCCESS = 0
        const val RESULT_FAILURE = 1

        @Volatile
        private var instance: FairMemoryReceiver? = null

        fun getInstance(): FairMemoryReceiver {
            return instance ?: synchronized(this) {
                instance ?: FairMemoryReceiver().also { instance = it }
            }
        }
    }

    private var remoteBinder: IBinder? = null
    @Volatile private var initialized = false
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var appContext: Context? = null

    private val listenerLock = Any()
    private var listenerRef: WeakReference<MemoryListener>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action != ACTION_TRIM && action != ACTION_KILL) return

            Log.i(TAG, "收到广播: $action")

            val data = intent.extras ?: return
            val commonBundle = data.getBundle(BUNDLE_KEY_COMMON) ?: return
            val extraBundle = data.getBundle(BUNDLE_KEY_EXTRA)

            val notifyType = commonBundle.getInt(KEY_NOTIFY_TYPE)
            val notifyId = commonBundle.getInt(KEY_NOTIFY_ID)
            val reason = commonBundle.getString(KEY_REASON)
            val callbackBinder = commonBundle.getBinder(KEY_CALLBACK)

            // 解析 extra 数据（日志用）
            if (extraBundle != null) {
                val heapAlloc = extraBundle.getInt(KEY_HEAP_ALLOC)
                val heapCapacity = extraBundle.getInt(KEY_HEAP_CAPACITY)
                val pss = extraBundle.getInt(KEY_PSS)
                val pssLimit = extraBundle.getInt(KEY_PSS_LIMIT)
                val typeName = if (notifyType == NOTIFY_TYPE_PSS) "物理内存异常" else "Java堆内存异常"
                Log.i(TAG, "$typeName | notifyId=$notifyId | reason=$reason")
                if (notifyType == NOTIFY_TYPE_HEAP) {
                    Log.i(TAG, "堆内存: ${heapAlloc}KB / ${heapCapacity}KB")
                } else {
                    Log.i(TAG, "物理内存: ${pss}KB / ${pssLimit}KB")
                }
            }

            if (callbackBinder == null) {
                Log.w(TAG, "callback IBinder 未找到，无法回调系统")
                return
            }

            handleReceived(action, notifyType, notifyId, callbackBinder)
        }
    }

    /**
     * 初始化：注册广播接收器。
     * 应在 Application.onCreate() 中调用。
     */
    fun initialize(context: Context) {
        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext

            val ht = HandlerThread(TAG)
            ht.start()
            handlerThread = ht
            handler = Handler(ht.looper)

            val filter = IntentFilter().apply {
                addAction(ACTION_TRIM)
                addAction(ACTION_KILL)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver, filter, null, handler,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, filter, null, handler)
            }

            initialized = true
            Log.i(TAG, "公平运行内存机制已初始化，监听 TRIM/KILL 广播")
        }
    }

    /**
     * 销毁：注销广播接收器并停止 HandlerThread。
     * 通常无需手动调用（单例随进程退出而销毁），在测试或需要重新初始化时使用。
     */
    fun dispose() {
        synchronized(this) {
            if (!initialized) return
            appContext?.unregisterReceiver(receiver)
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            appContext = null
            val remote = remoteBinder
            if (remote != null) {
                try {
                    remote.unlinkToDeath(this, 0)
                } catch (_: Exception) {
                }
                remoteBinder = null
            }
            initialized = false
            Log.i(TAG, "公平运行内存机制已销毁")
        }
    }

    /**
     * 注册内存压力监听器。使用 [WeakReference] 持有，避免泄漏 ViewModel。
     * 传 null 可注销。可在 ViewModel 的 [androidx.lifecycle.ViewModel.onCleared] 中调用。
     */
    fun setMemoryListener(listener: MemoryListener?) {
        synchronized(listenerLock) {
            listenerRef = listener?.let { WeakReference(it) }
        }
    }

    /** 通知监听器执行 TRIM 释放（在 HandlerThread 上同步调用）。 */
    private fun notifyTrim(notifyType: Int) {
        synchronized(listenerLock) {
            listenerRef?.get()?.onTrimMemory(notifyType)
        }
    }

    /** 通知监听器执行 KILL 保存（在 HandlerThread 上同步调用）。 */
    private fun notifyKill() {
        synchronized(listenerLock) {
            listenerRef?.get()?.onKillMemory()
        }
    }

    /**
     * 处理收到的广播：释放内存 / 保存数据，然后回调系统。
     */
    private fun handleReceived(
        action: String,
        notifyType: Int,
        notifyId: Int,
        callback: IBinder
    ) {
        // 确保 binder 关联了 DeathRecipient
        if (!checkRemote(callback)) {
            Log.w(TAG, "无法关联 callback IBinder")
            return
        }

        when (action) {
            ACTION_TRIM -> {
                Log.i(TAG, "TRIM: 正在释放非关键内存… (notifyType=$notifyType)")
                // 通知 ViewModel 按 notifyType 差异化释放内存
                notifyTrim(notifyType)
            }
            ACTION_KILL -> {
                Log.i(TAG, "KILL: 正在保存应用状态…")
                // 通知 ViewModel 保存关键状态（如刷新未提交的会话）
                notifyKill()
                // 蓝牙心率数据已通过 Room 数据库持久化，无需额外操作
            }
        }

        // 回调系统，通知处理完成
        val extra = Bundle().apply {
            putString("reply", "HeartRateMonitor: $action processed")
        }
        reply(notifyType, notifyId, RESULT_SUCCESS, extra)
    }

    /**
     * 关联 callback IBinder 并注册 DeathRecipient。
     */
    private fun checkRemote(callback: IBinder): Boolean {
        synchronized(this) {
            if (remoteBinder == null) {
                try {
                    remoteBinder = callback
                    callback.linkToDeath(this, 0)
                } catch (e: RemoteException) {
                    remoteBinder = null
                    return false
                }
            }
            return true
        }
    }

    /**
     * 通过 Binder 回调系统，返回处理结果。
     * 必须在收到广播后 3 秒内完成。
     */
    private fun reply(notifyType: Int, notifyId: Int, result: Int, extra: Bundle?) {
        synchronized(this) {
            val remote = remoteBinder ?: return
            val data = Parcel.obtain()
            try {
                data.writeInt(notifyType)
                data.writeInt(notifyId)
                data.writeInt(result)
                data.writeBundle(extra ?: Bundle())
                // FLAG_ONEWAY：单向调用，无需读取返回值；移除原 readException()（ONEWAY 下 reply 为空）
                remote.transact(TRANSACTION_EXCEPTION_REPLY, data, null, IBinder.FLAG_ONEWAY)
                Log.i(TAG, "已回调系统: result=$result")
            } catch (e: Exception) {
                Log.e(TAG, "回调系统失败", e)
            } finally {
                data.recycle()
            }
        }
    }

    /**
     * 系统 Binder 死亡时的回调。
     */
    override fun binderDied() {
        synchronized(this) {
            val remote = remoteBinder
            if (remote != null) {
                try {
                    remote.unlinkToDeath(this, 0)
                } catch (e: Exception) {
                    // ignore
                }
            }
            remoteBinder = null
            Log.w(TAG, "系统 callback IBinder 已死亡")
        }
    }
}
