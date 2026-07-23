package com.github.heartratemonitor_compose.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.main.MainActivity

/**
 * 公平运行内存机制的用户通知器。
 *
 * 当应用在前台收到 TRIM 广播且达到查杀条件时，向用户推送差异化通知：
 * - Java 堆内存异常：提示用户手动关闭应用，提供“关闭应用”与“忽略”操作。
 * - 物理内存异常：提示应用即将/已被系统关闭，提供“重新打开”操作。
 *
 * 通知渠道在 [initialize] 时创建；关闭应用的动作通过私有广播接收器处理。
 */
object FairMemoryNotifier {

    private const val TAG = "FairMemoryNotifier"

    private const val HEAP_CHANNEL_ID = "fair_memory_heap"
    private const val PSS_CHANNEL_ID = "fair_memory_pss"

    private const val HEAP_NOTIFICATION_ID = 1001
    private const val PSS_NOTIFICATION_ID = 1002

    private const val ACTION_CLOSE_APP = "com.github.heartratemonitor_compose.action.CLOSE_APP"
    private const val ACTION_DISMISS_HEAP = "com.github.heartratemonitor_compose.action.DISMISS_HEAP_NOTIFICATION"

    @Volatile
    private var initialized = false
    private var closeAppReceiver: BroadcastReceiver? = null

    /**
     * 初始化通知渠道与关闭应用广播接收器。
     * 应在 [Application.onCreate] 中调用。
     */
    fun initialize(context: Context) {
        if (initialized) return

        createNotificationChannels(context)
        registerActionReceiver(context)

        initialized = true
        Log.i(TAG, "公平运行内存通知器已初始化")
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val heapChannel = NotificationChannel(
            HEAP_CHANNEL_ID,
            context.getString(R.string.fair_memory_heap_channel_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.fair_memory_heap_channel_desc)
        }

        val pssChannel = NotificationChannel(
            PSS_CHANNEL_ID,
            context.getString(R.string.fair_memory_pss_channel_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.fair_memory_pss_channel_desc)
        }

        manager.createNotificationChannels(listOf(heapChannel, pssChannel))
    }

    private fun registerActionReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_CLOSE_APP -> closeApplication(context)
                    ACTION_DISMISS_HEAP -> dismissHeapNotification(context)
                }
            }
        }
        closeAppReceiver = receiver

        val filter = IntentFilter().apply {
            addAction(ACTION_CLOSE_APP)
            addAction(ACTION_DISMISS_HEAP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        ContextCompat.registerReceiver(context, receiver, filter, flags)
    }

    private fun closeApplication(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(HEAP_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        Log.i(TAG, "用户选择关闭应用")
        Process.killProcess(Process.myPid())
    }

    private fun dismissHeapNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(HEAP_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        Log.i(TAG, "用户忽略 Java 堆内存异常通知")
    }

    /**
     * 显示 Java 堆内存异常通知。
     * 仅在应用处于前台时推送，避免后台打扰用户。
     */
    fun showHeapMemoryNotification(context: Context) {
        if (!isAppInForeground()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val closeIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_CLOSE_APP).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(ACTION_DISMISS_HEAP).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HEAP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fair_memory)
            .setContentTitle(context.getString(R.string.fair_memory_heap_notification_title))
            .setContentText(context.getString(R.string.fair_memory_heap_notification_text))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.fair_memory_heap_notification_text)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.fair_memory_close_app), closeIntent)
            .addAction(0, context.getString(R.string.fair_memory_ignore), dismissIntent)
            .build()

        manager.notify(HEAP_NOTIFICATION_ID, notification)
        Log.i(TAG, "已显示 Java 堆内存异常通知")
    }

    /**
     * 显示物理内存异常通知。
     * 仅在应用处于前台时推送；通知会保留在通知栏，即使系统随后查杀应用，用户仍可点击重新打开。
     */
    fun showPssMemoryNotification(context: Context) {
        if (!isAppInForeground()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reopenIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PSS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fair_memory)
            .setContentTitle(context.getString(R.string.fair_memory_pss_notification_title))
            .setContentText(context.getString(R.string.fair_memory_pss_notification_text))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.fair_memory_pss_notification_text)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setContentIntent(reopenIntent)
            .addAction(0, context.getString(R.string.fair_memory_reopen_app), reopenIntent)
            .build()

        manager.notify(PSS_NOTIFICATION_ID, notification)
        Log.i(TAG, "已显示物理内存异常通知")
    }

    private fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.RESUMED)
    }
}
