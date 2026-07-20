package com.github.heartratemonitor_compose.ui.webhook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Webhook 配置页面的 ViewModel。
 *
 * 通过 [Application.appContainer] 获取 [WebhookRepository]，避免 UI 层直接操作系统服务/网络管理类。
 */
class WebhookViewModel(application: Application) : AndroidViewModel(application) {

    private val webhookRepository: WebhookRepository = application.appContainer.webhookRepository

    private val _webhooks = MutableStateFlow<List<Webhook>>(emptyList())
    val webhooks: StateFlow<List<Webhook>> = _webhooks.asStateFlow()

    init {
        loadWebhooks()
    }

    fun loadWebhooks() {
        viewModelScope.launch(Dispatchers.IO) {
            _webhooks.value = webhookRepository.getWebhooks()
        }
    }

    fun saveWebhooks(webhooks: List<Webhook>) {
        viewModelScope.launch(Dispatchers.IO) {
            webhookRepository.saveWebhooks(webhooks)
            _webhooks.value = webhookRepository.getWebhooks()
        }
    }

    fun deleteWebhook(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _webhooks.value.toMutableList().apply { removeAt(index) }
            webhookRepository.saveWebhooks(updated)
            _webhooks.value = webhookRepository.getWebhooks()
        }
    }

    fun testWebhook(webhook: Webhook, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            webhookRepository.testWebhook(webhook, onResult)
        }
    }
}
