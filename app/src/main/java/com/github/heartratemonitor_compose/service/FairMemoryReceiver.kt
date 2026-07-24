package com.github.heartratemonitor_compose.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * 公平运行内存机制适配器。
 *
 * 金标联盟（vivo、小米、OPPO、荣耀）定义的两个系统广播：
 * - [ACTION_TRIM]：系统内存紧张时通知应用主动释放非关键内存
 * - [ACTION_KILL]：系统即将查杀应用，通知应用保存状态并释放资源
 *
 * 实现要点：
 * 1. 动态注册广播接收器，使用 [Context.RECEIVER_EXPORTED]（API 33+）
 * 2. 解析 Intent extras 中的 common / extra 两个 Bundle
 * 3. 提取 common 中的 callback IBinder
 * 4. 在 3 秒内通过 IBinder.transact() 回调处理结果
 * 5. 实现 [IBinder.DeathRecipient] 监听系统服务死亡
 * 6. 使用独立 HandlerThread 处理广播
 * 7. 支持多监听器注册表，统一向 BleService、各 ViewModel 等分发内存压力事件
 * 8. 所有监听器调用包裹 try-catch，防止单个监听器异常导致系统回调超时
 */
class FairMemoryReceiver private constructor() : IBinder.DeathRecipient {

    /**
     * 内存压力事件监听器。
     *
     * 由 ViewModel、Service 或其他需要响应内存压力的组件实现，在收到 TRIM/KILL 广播时
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
    private val listenerRefs = mutableListOf<WeakReference<MemoryListener>>()

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

            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                null,
                handler,
                ContextCompat.RECEIVER_EXPORTED
            )

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
     * 注册内存压力监听器。使用 [WeakReference] 持有，避免泄漏。
     * 可在 ViewModel 的 [androidx.lifecycle.ViewModel.onCleared] 或服务销毁时调用 [removeMemoryListener]。
     */
    fun addMemoryListener(listener: MemoryListener) {
        synchronized(listenerLock) {
            // 避免重复注册同一对象
            if (listenerRefs.none { it.get() === listener }) {
                listenerRefs.add(WeakReference(listener))
            }
        }
    }

    /**
     * 注销内存压力监听器。
     */
    fun removeMemoryListener(listener: MemoryListener) {
        synchronized(listenerLock) {
            listenerRefs.removeAll { it.get() === listener || it.get() == null }
        }
    }

    /** 通知所有监听器执行 TRIM 释放（在 HandlerThread 上同步调用）。 */
    private fun notifyTrim(notifyType: Int) {
        val listeners = synchronized(listenerLock) {
            listenerRefs.removeAll { it.get() == null }
            listenerRefs.mapNotNull { it.get() }
        }
        for (listener in listeners) {
            try {
                listener.onTrimMemory(notifyType)
            } catch (e: Exception) {
                Log.e(TAG, "监听器 onTrimMemory 异常，继续执行下一个", e)
            }
        }
    }

    /** 通知所有监听器执行 KILL 保存（在 HandlerThread 上同步调用）。 */
    private fun notifyKill() {
        val listeners = synchronized(listenerLock) {
            listenerRefs.removeAll { it.get() == null }
            listenerRefs.mapNotNull { it.get() }
        }
        for (listener in listeners) {
            try {
                listener.onKillMemory()
            } catch (e: Exception) {
                Log.e(TAG, "监听器 onKillMemory 异常，继续执行下一个", e)
            }
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
        // 确保 binder 关联了 DeathRecipient（每次都更新为当前 callback）
        if (!checkRemote(callback)) {
            Log.w(TAG, "无法关联 callback IBinder")
            return
        }

        when (action) {
            ACTION_TRIM -> {
                Log.i(TAG, "TRIM: 正在释放非关键内存… (notifyType=$notifyType)")
                // 通知所有已注册组件按 notifyType 差异化释放内存
                notifyTrim(notifyType)
                // 在前台且达到查杀条件时，向用户推送差异化提示
                appContext?.let { ctx ->
                    when (notifyType) {
                        NOTIFY_TYPE_HEAP -> FairMemoryNotifier.showHeapMemoryNotification(ctx)
                        NOTIFY_TYPE_PSS -> FairMemoryNotifier.showPssMemoryNotification(ctx)
                    }
                }
            }
            ACTION_KILL -> {
                Log.i(TAG, "KILL: 正在保存应用状态…")
                // 通知所有已注册组件保存关键状态
                notifyKill()
            }
        }

        // 回调系统，通知处理完成（使用当前广播的 callback，而非缓存值）
        val extra = Bundle().apply {
            putString("reply", "HeartRateMonitor: $action processed")
        }
        reply(callback, notifyType, notifyId, RESULT_SUCCESS, extra)
    }

    /**
     * 关联 callback IBinder 并注册 DeathRecipient。
     * 若 callback 与已缓存的不同，先解除旧绑定再关联新的，确保死亡监控指向正确对象。
     */
    private fun checkRemote(callback: IBinder): Boolean {
        synchronized(this) {
            // 已关联且是同一个 binder，直接复用
            if (remoteBinder === callback) return true

            // 先解除旧的死亡监听
            remoteBinder?.let { old ->
                try {
                    old.unlinkToDeath(this, 0)
                } catch (_: Exception) {
                    // 旧 binder 可能已死亡，忽略
                }
            }

            // 关联新的 binder
            return try {
                callback.linkToDeath(this, 0)
                remoteBinder = callback
                true
            } catch (e: RemoteException) {
                remoteBinder = null
                false
            }
        }
    }

    /**
     * 通过 Binder 回调系统，返回处理结果。
     * 必须在收到广播后 3 秒内完成。
     */
    private fun reply(callback: IBinder, notifyType: Int, notifyId: Int, result: Int, extra: Bundle?) {
        synchronized(this) {
            val data = Parcel.obtain()
            try {
                data.writeInt(notifyType)
                data.writeInt(notifyId)
                data.writeInt(result)
                data.writeBundle(extra ?: Bundle())
                // FLAG_ONEWAY：单向调用，无需读取返回值；移除原 readException()（ONEWAY 下 reply 为空）
                callback.transact(TRANSACTION_EXCEPTION_REPLY, data, null, IBinder.FLAG_ONEWAY)
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
