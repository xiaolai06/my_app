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
    val homeOrder:List<String> = listOf("weather","suggestions","favorites","events","tasks","habits","life","budget","recent","inbox"),
    val hiddenCards:Set<String> = emptySet(),val shortcuts:List<String> = listOf("note","task","event","expense"),val upcomingDays:Int=7,
    val homeStyle:String="color")
class PreferencesRepository(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val reduce = booleanPreferencesKey("reduce_motion")
    private val frost = booleanPreferencesKey("frosted")
    private val dashboardKey=stringPreferencesKey("dashboard_v1")
    private val weatherKey=stringPreferencesKey("weather_v1")
    private val toolLayoutsKey=stringPreferencesKey("tool_layouts_v1")
    private val paymentChannelKey=stringPreferencesKey("ledger_default_payment_channel")
    val defaultPaymentChannel=context.settings.data.map {it[paymentChannelKey].orEmpty()}
    suspend fun setDefaultPaymentChannel(value:String) {
        require(value in setOf("","微信","支付宝","银行卡","现金","其他")) {"请选择有效支付方式"}
        context.settings.edit {it[paymentChannelKey]=value}
    }
    val toolLayouts=context.settings.data.map {values->
        runCatching {val json=org.json.JSONObject(values[toolLayoutsKey] ?: "{}")
            json.keys().asSequence().mapNotNull {key->json.optString(key).takeIf {it in setOf("list","grid")}?.let {key to it}}.toMap()
        }.getOrDefault(emptyMap())
    }
    suspend fun setToolLayout(page:String,layout:String) {
        require(page in setOf("wardrobe","outfit","item","checklist","anniversary","subscription","bill","loan","project"))
        require(layout in setOf("list","grid"))
        context.settings.edit {values->
            val json=runCatching {org.json.JSONObject(values[toolLayoutsKey] ?: "{}")} .getOrDefault(org.json.JSONObject())
            values[toolLayoutsKey]=json.put(page,layout).toString()
        }
    }
    // Keep persisted keys so upgrading preserves the user's notification choice.
    private val focusNotificationKey=booleanPreferencesKey("focus_live_enabled")
    private val focusHiddenKey=stringPreferencesKey("focus_live_hidden")
    val focusNotifications=context.settings.data.map {(it[focusNotificationKey] ?: true) to it[focusHiddenKey].orEmpty()}
    suspend fun hideFocusNotification(id:String) {context.settings.edit {it[focusHiddenKey]=id}}
    suspend fun setFocusNotifications(enabled:Boolean) {context.settings.edit {it[focusNotificationKey]=enabled;if(enabled) it.remove(focusHiddenKey)}}
    val weather=context.settings.data.map {values->
        runCatching {val j=org.json.JSONObject(values[weatherKey] ?: "{}")
            val place=if(j.has("latitude")) WeatherPlace(j.getString("city"),j.getDouble("latitude"),j.getDouble("longitude")).also {it.validate()} else null
            WeatherSettings(j.optBoolean("enabled") && place!=null,place)
        }.getOrDefault(WeatherSettings())
    }
    suspend fun setWeather(value:WeatherSettings) {
        value.place?.validate();require(!value.enabled || value.place!=null) {"请先选择城市"}
        context.settings.edit {values->
            val j=org.json.JSONObject().put("enabled",value.enabled)
            value.place?.let {j.put("city",it.name).put("latitude",it.latitude).put("longitude",it.longitude)}
            values[weatherKey]=j.toString()
        }
    }
    val dashboard=context.settings.data.map {values->
        val defaults=DashboardPreferences()
        runCatching {
            val j=org.json.JSONObject(values[dashboardKey] ?: "{}")
            fun list(k:String,default:List<String>):List<String> = j.optJSONArray(k)?.let {a->(0 until a.length()).map {a.getString(it)}} ?: default
            val order=(list("order",defaults.homeOrder).filter {it in defaults.homeOrder}+defaults.homeOrder).distinct()
            DashboardPreferences(j.optString("nickname").take(30),j.optBoolean("hideAmounts"),order,
                list("hidden",emptyList()).filter {it in defaults.homeOrder}.toSet(),
                (list("shortcuts",defaults.shortcuts).filter {it in defaults.shortcuts}+defaults.shortcuts).distinct(),j.optInt("upcomingDays",7).coerceIn(1,30),
                j.optString("homeStyle","color").takeIf {it in setOf("color","plain")} ?: "color")
        }.getOrDefault(defaults)
    }
    suspend fun setDashboard(value:DashboardPreferences) {context.settings.edit {values->
        val j=org.json.JSONObject().put("nickname",value.nickname.trim().take(30)).put("hideAmounts",value.hideAmounts)
            .put("order",org.json.JSONArray(value.homeOrder)).put("hidden",org.json.JSONArray(value.hiddenCards.toList()))
            .put("shortcuts",org.json.JSONArray(value.shortcuts)).put("upcomingDays",value.upcomingDays.coerceIn(1,30)).put("homeStyle",value.homeStyle)
        values[dashboardKey]=j.toString()
    }}
    val appearance = context.settings.data.map { Appearance(it[theme] ?: "system", it[reduce] ?: false, it[frost] ?: false) }
    suspend fun set(value: Appearance) { context.settings.edit {
        it[theme] = value.theme; it[reduce] = value.reducedMotion; it[frost] = value.frosted
    } }
}
