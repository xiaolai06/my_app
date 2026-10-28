package com.example.dailylife

import android.app.Application
import androidx.room.Room
import com.example.dailylife.data.local.DailyLifeDatabase
import com.example.dailylife.data.local.DatabaseSafety
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.repository.RecordsRepository
import com.example.dailylife.data.repository.PreferencesRepository
import com.example.dailylife.data.repository.PlansRepository
import com.example.dailylife.data.repository.AndroidFocusClock
import com.example.dailylife.data.repository.PlanAlarms
import com.example.dailylife.data.repository.LedgerRepository
import com.example.dailylife.data.repository.ToolsRepository
import com.example.dailylife.data.files.RestoreWorkspaces
import kotlinx.coroutines.*

class DailyLifeApplication : Application() {
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        backgroundScope.launch { runCatching { RestoreWorkspaces.cleanup(cacheDir) } }
    }
    val database by lazy {
        Room.databaseBuilder(this, DailyLifeDatabase::class.java, "daily-life.db").build()
    }
    val records by lazy { RecordsRepository(database, AttachmentStore(this)) {
        DatabaseSafety.beforeOpen(this,"daily-life.db")
    } }
    val plans by lazy { PlansRepository(records, AndroidFocusClock(this)) }
    val planAlarms by lazy { PlanAlarms(this,plans) }
    val ledger by lazy {LedgerRepository(records)}
    val tools by lazy {ToolsRepository(database,records)}
    val preferences by lazy { PreferencesRepository(this) }
    val weather by lazy { com.example.dailylife.data.repository.WeatherRepository(this) }
}
