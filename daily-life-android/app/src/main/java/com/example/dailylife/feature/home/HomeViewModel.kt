package com.example.dailylife.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.repository.DashboardPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(app:Application):AndroidViewModel(app) {
    private val preferences=(app as DailyLifeApplication).preferences
    val state=preferences.dashboard.stateIn(viewModelScope,SharingStarted.Eagerly,DashboardPreferences())
    private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow()
    fun update(value:DashboardPreferences) {viewModelScope.launch {runCatching {preferences.setDashboard(value)}.onFailure {_error.value="偏好保存失败，请重试"}}}
    fun clearError() {_error.value=null}
}
