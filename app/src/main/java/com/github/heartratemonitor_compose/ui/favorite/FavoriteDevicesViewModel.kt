package com.github.heartratemonitor_compose.ui.favorite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceEntity
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.FavoriteDeviceRepository
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 收藏设备页面 ViewModel。
 *
 * 将收藏设备列表与当前收藏设备 ID 的管理从 Composable 移入 ViewModel，
 * UI 层仅订阅状态并触发增删操作。
 */
class FavoriteDevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val favoriteDeviceRepository: FavoriteDeviceRepository = container.favoriteDeviceRepository
    private val settings: SettingsRepository = container.settingsRepository

    val favorites: StateFlow<List<FavoriteDeviceEntity>> =
        favoriteDeviceRepository.getAllFavorites(application)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _favoriteDeviceId = MutableStateFlow(
        favoriteDeviceRepository.getFavoriteDeviceId(application)
    )
    val favoriteDeviceId: StateFlow<String?> = _favoriteDeviceId.asStateFlow()

    init {
        _favoriteDeviceId.value = settings.getStringNullable(PrefsKeys.FAVORITE_DEVICE_ID)
        viewModelScope.launch {
            settings.observeStringNullable(PrefsKeys.FAVORITE_DEVICE_ID).collect {
                _favoriteDeviceId.value = it
            }
        }
    }

    fun addFavorite(device: FavoriteDeviceEntity) {
        viewModelScope.launch {
            favoriteDeviceRepository.addFavorite(getApplication(), device)
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            favoriteDeviceRepository.removeFavorite(app, id)
            // 如果删除的是当前收藏设备，从剩余收藏中找最近的一个设为当前收藏，
            // 与 MainViewModel.toggleFavoriteDevice 行为一致。
            if (_favoriteDeviceId.value == id) {
                val latestFavorite = favoriteDeviceRepository.getLatestFavoriteDevice(app)
                if (latestFavorite != null) {
                    favoriteDeviceRepository.setFavoriteDeviceId(app, latestFavorite.id)
                } else {
                    favoriteDeviceRepository.clearFavoriteDeviceId(app)
                }
            }
        }
    }

    fun clearAllFavorites() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            favoriteDeviceRepository.clearAllFavorites(app)
            favoriteDeviceRepository.clearFavoriteDeviceId(app)
        }
    }
}
