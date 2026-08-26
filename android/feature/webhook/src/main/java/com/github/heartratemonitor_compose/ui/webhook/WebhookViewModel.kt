package com.github.heartratemonitor_compose.ui.webhook

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Webhook 配置页面的 ViewModel（MVI 架构，Phase 2）。
 *
 * Webhook 列表归约进单一 [WebhookUiState]；读写仍经 [WebhookRepository]
 * （触发链路保持契约 5：triggerWebhooks 节流不受本迁移影响）。
 * [WebhookRepository] 由 Hilt 构造注入（Phase 3 起，替代 `application as WebhookDependencies`）。
 */
@HiltViewModel
class WebhookViewModel @Inject constructor(
    private val webhookRepository: WebhookRepository
) : MviViewModel<WebhookUiState, WebhookIntent>(WebhookUiState()) {

    init {
        dispatch(WebhookIntent.Load)
    }

    override suspend fun handleIntent(intent: WebhookIntent) {
        when (intent) {
            WebhookIntent.Load -> {
                val loaded = withContext(Dispatchers.IO) { webhookRepository.getWebhooks().toImmutableList() }
                setState { it.copy(webhooks = loaded) }
            }
            is WebhookIntent.Save -> withContext(Dispatchers.IO) {
                webhookRepository.saveWebhooks(intent.webhooks)
                setState { it.copy(webhooks = webhookRepository.getWebhooks().toImmutableList()) }
            }
            is WebhookIntent.Delete -> withContext(Dispatchers.IO) {
                val updated = currentState.webhooks.toMutableList().apply { removeAt(intent.index) }.toImmutableList()
                webhookRepository.saveWebhooks(updated)
                setState { it.copy(webhooks = webhookRepository.getWebhooks().toImmutableList()) }
            }
            is WebhookIntent.Test -> {
                // 结果经回调回传（§3.4 方案 1）：测试响应由 UI 瞬时态 testResponse 展示
                viewModelScope.launch(Dispatchers.IO) {
                    webhookRepository.testWebhook(intent.webhook, intent.onResult)
                }
            }
        }
    }
}

/** Webhook 配置页用户意图。 */
sealed interface WebhookIntent {
    data object Load : WebhookIntent
    data class Save(val webhooks: ImmutableList<Webhook>) : WebhookIntent
    data class Delete(val index: Int) : WebhookIntent

    /** 测试发送；结果经 [onResult] 回调（一次性事件走 VM 回调，§3.4 方案 1）。 */
    data class Test(val webhook: Webhook, val onResult: (String) -> Unit) : WebhookIntent
}

/** Webhook 配置页 UI 状态（只读快照）。 */
data class WebhookUiState(
    val webhooks: ImmutableList<Webhook> = persistentListOf()
)
