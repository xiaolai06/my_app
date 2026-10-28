package com.example.dailylife.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

class HomeViewModel(app:Application):AndroidViewModel(app) {
    private val preferences=(app as DailyLifeApplication).preferences
    private val weatherRepository=(app as DailyLifeApplication).weather
    private val _weather=MutableStateFlow(WeatherUi());val weather=_weather.asStateFlow()
    val focusNotifications=preferences.focusNotifications.catch {emit(false to "")}.stateIn(viewModelScope,SharingStarted.Eagerly,false to "")
    fun setFocusNotifications(enabled:Boolean) {viewModelScope.launch {
        try {preferences.setFocusNotifications(enabled);(getApplication<Application>() as DailyLifeApplication).planAlarms.reconcile()}
        catch(e:Exception) {if(e is CancellationException) throw e;_error.value="专注通知设置未完成，请重试"}
    }}
    private var weatherJob:Job?=null
    private var searchJob:Job?=null
    init {viewModelScope.launch {
        preferences.weather.catch {_weather.update {it.copy(error="天气设置读取失败，联网已暂停")};emit(WeatherSettings())}.collectLatest {settings->
            weatherJob?.cancelAndJoin()
            _weather.update {it.copy(settings=settings,reading=null,error=null,loading=false)}
            if(settings.enabled) refreshWeather()
        }
    }}
    fun setWeather(value:WeatherSettings,onSaved:()->Unit={}) {viewModelScope.launch {
        try {preferences.setWeather(value);onSaved()} catch(e:Exception) {if(e is CancellationException) throw e;_weather.update {it.copy(error=e.message ?: "天气设置未保存")}}
    }}
    fun searchCities(query:String) {
        searchJob?.cancel()
        searchJob=viewModelScope.launch {
            _weather.update {it.copy(searching=true,places=emptyList(),error=null)}
            try {val result=weatherRepository.search(query);_weather.update {it.copy(places=result,error=if(result.isEmpty()) "未找到城市，可尝试拼音或手动填写城市坐标" else null)}}
            catch(e:Exception) {if(e is CancellationException) throw e;_weather.update {it.copy(error=e.message ?: "城市查询暂不可用")}}
            finally {_weather.update {it.copy(searching=false)}}
        }
    }
    fun refreshWeather(force:Boolean=false) {
        val settings=_weather.value.settings;val place=settings.place ?: return
        if(!settings.enabled || weatherJob?.isActive==true) return
        weatherJob=viewModelScope.launch {
            _weather.update {it.copy(loading=true,error=null)}
            try {
                val cached=weatherRepository.cached(place);_weather.update {it.copy(reading=cached)}
                val reading=weatherRepository.load(place,force);_weather.update {it.copy(reading=reading)}
            } catch(e:Exception) {if(e is CancellationException) throw e;_weather.update {it.copy(error="天气暂未更新，请检查网络后重试")}}
            finally {_weather.update {it.copy(loading=false)}}
        }
    }
    private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow()
    val state=preferences.dashboard.retryWhen {cause,attempt->
        if(cause is CancellationException) throw cause
        _error.value="首页偏好读取失败，正在重试；尚未读取时金额保持隐藏"
        delay(((attempt+1)*1000).coerceAtMost(30000));true
    }.stateIn(viewModelScope,SharingStarted.Eagerly,DashboardPreferences(hideAmounts=true))
    fun update(value:DashboardPreferences) {saveDashboard(value) {error->if(error!=null) _error.value=error}}
    fun saveDashboard(value:DashboardPreferences,result:(String?)->Unit) {viewModelScope.launch {
        try {preferences.setDashboard(value);result(null)} catch(e:Exception) {
            if(e is CancellationException) throw e
            result("偏好保存失败，请重试")
        }
    }}
    fun clearError() {_error.value=null}
}
