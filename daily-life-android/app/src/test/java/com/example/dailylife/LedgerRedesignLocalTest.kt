package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import com.example.dailylife.feature.home.HomeViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.example.dailylife.data.repository.Appearance
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Real Compose gestures and Room data: no preview-only bookkeeping. */
@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerRedesignLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val vm get()=ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {
            compose.waitForIdle()
            vm.ui.value.let {it.ready&&!it.busy} && records.ui.value.ready &&
                ViewModelProvider(compose.activity)[PlansViewModel::class.java].ui.value.ready &&
                ViewModelProvider(compose.activity)[ToolsViewModel::class.java].ui.value.ready
        }
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dismissMessage").performClick()
    }
    private fun ledger() {ready();compose.onNodeWithTag("root:账本").performClick();ready()}
    private fun tab(id:String) {
        val node=compose.onNodeWithTag("ledgerTab:$id")
        if(compose.activity.resources.configuration.fontScale>1.3f) node.performScrollTo()
        node.performClick();compose.waitForIdle()
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/reports/ledger-redesign/$name.png").apply {parentFile!!.mkdirs()}.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}
    @Before fun displayPreferences() {
        ready()
        runBlocking {
            app.preferences.set(Appearance(theme="light",reducedMotion=true))
            app.preferences.setDashboard(app.preferences.dashboard.first().copy(hideAmounts=false))
        }
        compose.waitUntil(15_000) {compose.waitForIdle();!ViewModelProvider(compose.activity)[HomeViewModel::class.java].state.value.hideAmounts && records.ui.value.appearance.theme=="light"}
    }

    @Test fun customCategoryAndCalendarSaveWithoutAccount() {
        ledger()
        listOf("overview","transactions","stats","accounts").forEach {compose.onNodeWithTag("ledgerTab:$it").assertExists()}
        compose.onNodeWithTag("ledgerTab:budgets").assertDoesNotExist()
        compose.onNodeWithTag("ledgerMore").assertDoesNotExist()
        compose.onNodeWithTag("hideLedgerAmounts").assertDoesNotExist()
        compose.onNodeWithTag("ledgerPeriod:month").assertIsSelected()
        compose.onNodeWithText("日常 ▾").assertDoesNotExist()
        compose.onNodeWithTag("reminderCenter").assertDoesNotExist()
        compose.onNodeWithTag("addLedgerTransaction").performClick();ready()
        compose.onNodeWithTag("ledgerAmount").performTextReplacement("28.50")
        compose.onNodeWithTag("ledgerSubject").performScrollTo().performTextInput("周末咖啡")
        shot("01-记一笔")
        compose.onNodeWithTag("ledgerDateSelect").performScrollTo().performClick()
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        val today=LocalDate.now();val date=today.minusDays(1)
        if(YearMonth.from(date)!=YearMonth.from(today)) compose.onNodeWithTag("calendarPrevious").performClick()
        compose.onNodeWithTag("calendarDay:$date").performScrollTo().performClick()
        shot("02-日期月历")
        compose.onNodeWithTag("ledgerCalendarCancel").performClick()
        assertEquals(today.toString(),vm.ui.value.editor!!.text("date"))
        compose.onNodeWithTag("ledgerDateSelect").performScrollTo().performClick()
        if(YearMonth.from(date)!=YearMonth.from(today)) compose.onNodeWithTag("calendarPrevious").performClick()
        compose.onNodeWithTag("calendarDay:$date").performScrollTo().performClick()
        compose.onNodeWithTag("ledgerCalendarConfirm").performClick()
        assertEquals(date.toString(),vm.ui.value.editor!!.text("date"))
        compose.onNodeWithTag("ledgerCategorySelect").performScrollTo().performClick()
        shot("03-分类选择")
        compose.onNodeWithTag("addLedgerCategory").performClick()
        compose.onNodeWithTag("newLedgerCategoryName").performTextInput("咖啡时光")
        compose.onNodeWithTag("saveNewLedgerCategory").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) {compose.waitForIdle();vm.ui.value.editor?.text("category")?.isNotBlank()==true&&!vm.ui.value.busy}
        val category=vm.ui.value.data.categories.single {it.name=="咖啡时光"}
        assertEquals(category.id,vm.ui.value.editor!!.text("category"))
        assertEquals("28.50",vm.ui.value.editor!!.text("amount"))
        compose.onNodeWithTag("saveLedgerTransaction").performClick();ready()
        compose.waitUntil(15_000) {compose.waitForIdle();vm.ui.value.editor==null || vm.ui.value.error!=null}
        assertNull(vm.ui.value.error,vm.ui.value.error)
        val saved=runBlocking {app.ledger.load()}.transactions.single {it.subject=="周末咖啡"}
        assertEquals(2850L,saved.amountMinor);assertEquals(date.toString(),saved.occurredOn)
        assertEquals(category.id,saved.categoryId);assertNull(saved.accountId);assertNull(saved.occurredTime)
        shot("04-消费详情")
    }

    private fun seed():String {
        ready()
        val snapshot=runBlocking {app.ledger.load()};val book=snapshot.defaultLedger()!!.id
        val date=LocalDate.now().withDayOfMonth(1).toString()
        runBlocking {
            app.ledger.saveAccount("日常银行卡","bank","5200.00",date,tail="1026")
            val bank=app.ledger.load().accounts.single {it.name=="日常银行卡"}.id
            listOf(Triple("午餐","餐饮","32.00"),Triple("地铁通勤","交通","6.00"),Triple("生活用品","购物","86.50")).forEach {(subject,category,amount)->
                val categoryId=snapshot.categories.single {it.name==category&&it.direction=="expense"}.id
                app.ledger.save(LedgerForm("expense").set("ledger",book).set("date",date).set("amount",amount).set("subject",subject).set("category",categoryId).set("account",bank))
            }
            app.ledger.save(LedgerForm("income").set("ledger",book).set("date",date).set("amount","8000").set("subject","本月工资").set("account",bank)
                .set("category",snapshot.categories.single {it.name=="工资"}.id))
        }
        compose.waitUntil(15_000) {compose.waitForIdle();vm.ui.value.data.transactions.size==4}
        return snapshot.categories.single {it.name=="餐饮"&&it.direction=="expense"}.id
    }

    @Test fun summariesDrillIntoMatchingDetailsAndSecondaryPagesReturn() {
        val food=seed();ledger()
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 124.50")
        shot("05-概况")
        compose.runOnIdle {vm.filter(LedgerTransactionFilter(query="不会匹配",from="2000-01-01",to="2000-01-02",type="income"))}
        tab("stats");shot("06-统计")
        compose.onNodeWithTag("ledgerStatistics").performScrollToNode(hasText("餐饮"))
        compose.onNodeWithText("餐饮",substring=false).performClick()
        compose.waitForIdle()
        assertEquals(LedgerTransactionFilter(type="expense",category=food),vm.ui.value.transactionFilter)
        compose.onNodeWithTag("ledgerTransactions").performScrollToNode(hasText("午餐"))
        compose.onNodeWithText("午餐").assertIsDisplayed()
        compose.onNodeWithTag("ledgerTypeFilter").performClick()
        compose.onNodeWithTag("ledgerFilterType:income").performClick()
        assertEquals(LedgerTransactionFilter(type="income"),vm.ui.value.transactionFilter)
        compose.onNodeWithTag("ledgerTransactions").performScrollToNode(hasText("本月工资"))
        compose.onNodeWithText("本月工资").assertIsDisplayed()
        compose.onNodeWithTag("ledgerTransactions").performScrollToIndex(0)
        compose.onNodeWithTag("clearLedgerFilters").performClick()
        assertEquals(LedgerTransactionFilter(),vm.ui.value.transactionFilter)
        compose.onNodeWithTag("ledgerTransactions").performScrollToIndex(0);shot("07-明细")
        tab("accounts");shot("08-账户")
        compose.onNodeWithText("日常银行卡").performScrollTo().performClick();ready()
        compose.onNodeWithTag("ledgerAccountFlow").assertExists()
        compose.onNodeWithTag("backLedgerPage").performClick();ready()
        compose.onNodeWithTag("ledgerTab:accounts").assertIsSelected()
        compose.onNodeWithTag("ledgerSettings").performScrollTo().performClick();ready()
        compose.onNodeWithText("预算",substring=false).performScrollTo().performClick();ready()
        compose.onNodeWithText("设置预算",substring=false).assertIsDisplayed()
        compose.onNodeWithTag("backLedgerPage").performClick();ready()
        compose.onNodeWithText("账本设置",substring=false).assertIsDisplayed()
        compose.onNodeWithTag("backLedgerPage").performClick();ready()
        compose.onNodeWithTag("ledgerTab:accounts").assertIsSelected()
    }

    @Test fun darkDateNavigationAndDirectionMenuShowActualPeriodTotals() {
        seed();ledger()
        compose.runOnIdle {records.appearance(records.ui.value.appearance.copy(theme="dark",reducedMotion=true))}
        compose.waitUntil(10_000) {records.ui.value.appearance.theme=="dark"}
        runBlocking {app.preferences.setDashboard(app.preferences.dashboard.first().copy(hideAmounts=true))}
        compose.waitUntil(10_000) {compose.waitForIdle();ViewModelProvider(compose.activity)[HomeViewModel::class.java].state.value.hideAmounts}
        compose.onNodeWithTag("hideLedgerAmounts").assertDoesNotExist()
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 124.50")
        compose.onNodeWithTag("ledgerDateRange").performClick()
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        val emptyDay=YearMonth.now().atDay(2).toString()
        compose.onNodeWithTag("calendarDay:$emptyDay").performScrollTo().performClick()
        compose.onNodeWithTag("confirmDate").assertTextContains("查看当天").performClick()
        compose.onNodeWithTag("ledgerPeriod:day").assertIsSelected()
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 0.00")
        compose.onNodeWithTag("ledgerDateRange").performClick()
        compose.onNodeWithTag("calendarChooseMonth").performClick()
        compose.onNodeWithTag("ledgerPeriod:month").assertIsSelected()
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 124.50");shot("09-深色日期概况")
        tab("stats")
        compose.onNodeWithTag("ledgerStatsTotal").assertTextEquals("¥ 124.50")
        compose.onNodeWithTag("ledgerStatsDirection").performClick()
        compose.onNodeWithTag("ledgerStatsDirection:expense").assertIsSelected()
        compose.onNodeWithTag("ledgerStatsDirection:income").performClick()
        compose.onNodeWithTag("ledgerStatsTotal").assertTextEquals("¥ 8000.00")
        shot("10-深色统计")
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontCalendarAndNavigationKeepFullTouchTargets() {
        ready()
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration);config.fontScale=2f
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ledger()
        listOf("overview","transactions","stats","accounts").forEach {id->
            tab(id);compose.onNodeWithTag("ledgerTab:$id").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        tab("overview");shot("11-大字体概况")
        compose.onNodeWithTag("addLedgerTransaction").assertIsDisplayed().performClick();ready()
        compose.onNodeWithTag("ledgerDateSelect").performScrollTo().performClick()
        compose.onNodeWithTag("calendarMode:grid").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("calendarMode:list").performScrollTo().performClick().assertIsSelected()
        val day=YearMonth.now().atDay(1)
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:$day"))
        compose.onNodeWithTag("calendarDay:$day").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val sunday=day.plusDays((7-day.dayOfWeek.value).toLong())
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:$sunday"))
        compose.onNodeWithTag("calendarDay:$sunday").performClick()
        shot("12-大字体日期列表")
        compose.onNodeWithTag("ledgerCalendarConfirm").assertIsDisplayed().performClick()
        assertEquals(sunday.toString(),vm.ui.value.editor!!.text("date"))
        compose.onNodeWithTag("ledgerCategorySelect").performScrollTo().performClick()
        shot("13-大字体分类")
        compose.onNodeWithTag("addLedgerCategory").assertIsDisplayed()
    }
}
