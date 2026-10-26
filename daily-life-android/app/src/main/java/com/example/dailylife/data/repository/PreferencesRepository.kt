package com.example.dailylife.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("appearance")
data class Appearance(val theme: String = "system", val reducedMotion: Boolean = false, val frosted: Boolean = false)
data class DashboardPreferences(val nickname:String="",val hideAmounts:Boolean=false,
    val homeOrder:List<String> = listOf("events","tasks","habits","life","budget","recent","inbox"),
    val hiddenCards:Set<String> = emptySet(),val shortcuts:List<String> = listOf("note","task","event","expense"),val upcomingDays:Int=7)
class PreferencesRepository(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val reduce = booleanPreferencesKey("reduce_motion")
    private val frost = booleanPreferencesKey("frosted")
    private val dashboardKey=stringPreferencesKey("dashboard_v1")
    val dashboard=context.settings.data.map {values->
        val defaults=DashboardPreferences()
        runCatching {
            val j=org.json.JSONObject(values[dashboardKey] ?: "{}")
            fun list(k:String,default:List<String>):List<String> = j.optJSONArray(k)?.let {a->(0 until a.length()).map {a.getString(it)}} ?: default
            val order=(list("order",defaults.homeOrder).filter {it in defaults.homeOrder}+defaults.homeOrder).distinct()
            DashboardPreferences(j.optString("nickname").take(30),j.optBoolean("hideAmounts"),order,
                list("hidden",emptyList()).filter {it in defaults.homeOrder}.toSet(),
                (list("shortcuts",defaults.shortcuts).filter {it in defaults.shortcuts}+defaults.shortcuts).distinct(),j.optInt("upcomingDays",7).coerceIn(1,30))
        }.getOrDefault(defaults)
    }
    suspend fun setDashboard(value:DashboardPreferences) {context.settings.edit {values->
        val j=org.json.JSONObject().put("nickname",value.nickname.trim().take(30)).put("hideAmounts",value.hideAmounts)
            .put("order",org.json.JSONArray(value.homeOrder)).put("hidden",org.json.JSONArray(value.hiddenCards.toList()))
            .put("shortcuts",org.json.JSONArray(value.shortcuts)).put("upcomingDays",value.upcomingDays.coerceIn(1,30))
        values[dashboardKey]=j.toString()
    }}
    val appearance = context.settings.data.map { Appearance(it[theme] ?: "system", it[reduce] ?: false, it[frost] ?: false) }
    suspend fun set(value: Appearance) { context.settings.edit {
        it[theme] = value.theme; it[reduce] = value.reducedMotion; it[frost] = value.frosted
    } }
}
