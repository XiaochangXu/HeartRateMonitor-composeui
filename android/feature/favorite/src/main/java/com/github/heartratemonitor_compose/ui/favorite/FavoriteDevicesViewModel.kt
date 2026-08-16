package com.github.heartratemonitor_compose.ui.favorite

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.model.FavoriteDeviceInfo
import com.github.heartratemonitor_compose.data.repository.FavoriteDeviceRepository
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 教科书式 MVI，Phase 2。收藏设备列表与当前收藏 ID 归约进单一 UiState，
 * UI 层仅订阅状态并经 Intent 触发增删。
 * 依赖由 Hilt 构造注入（Phase 3 起）。
 */
@HiltViewModel
class FavoriteDevicesViewModel @Inject constructor(
    private val favoriteDeviceRepository: FavoriteDeviceRepository,
    private val settings: SettingsRepository
) : MviViewModel<FavoriteDevicesUiState, FavoriteDevicesIntent>(
    FavoriteDevicesUiState(
        favoriteDeviceId = favoriteDeviceRepository.getFavoriteDeviceId()
    )
) {

    init {
        viewModelScope.launch {
            favoriteDeviceRepository.getAllFavorites().collect { list ->
                setState { it.copy(devices = list) }
            }
        }
        viewModelScope.launch {
            settings.observeNullable(SettingsKeys.FAVORITE_DEVICE_ID).collect { id ->
                setState { it.copy(favoriteDeviceId = id) }
            }
        }
    }

    override suspend fun handleIntent(intent: FavoriteDevicesIntent) {
        when (intent) {
            is FavoriteDevicesIntent.RemoveFavorite -> {
                // 如果删除的是当前收藏设备，删除后从剩余收藏中恢复最近的一个，
                // 与 MainViewModel.toggleFavoriteDevice 行为一致；否则仅删除记录。
                if (currentState.favoriteDeviceId == intent.id) {
                    favoriteDeviceRepository.deleteAndRestoreLatest(intent.id)
                } else {
                    favoriteDeviceRepository.removeFavorite(intent.id)
                }
            }
            FavoriteDevicesIntent.ClearAll -> {
                favoriteDeviceRepository.clearAllFavorites()
                favoriteDeviceRepository.clearFavoriteDeviceId()
            }
        }
    }
}

/** 收藏设备页用户意图。 */
sealed interface FavoriteDevicesIntent {
    data class RemoveFavorite(val id: String) : FavoriteDevicesIntent
    data object ClearAll : FavoriteDevicesIntent
}

/** 收藏设备页 UI 状态（只读快照）。 */
data class FavoriteDevicesUiState(
    val devices: List<FavoriteDeviceInfo> = emptyList(),
    val favoriteDeviceId: String? = null
)
