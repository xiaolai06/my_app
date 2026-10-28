package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.Attachment
import com.example.dailylife.data.local.AttachmentLink
import com.example.dailylife.data.local.TransactionTemplate
import com.example.dailylife.data.local.newId
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.home.HomeViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/** Real ViewModel, Room and Compose host; financial assertions read the committed repository. */
@RunWith(RobolectricTestRunner::class)
@Config(application = DailyLifeApplication::class, qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerExperienceLocalTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val vm get() = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
    private val records get() = ViewModelProvider(compose.activity)[RecordsViewModel::class.java]

    private fun ready() {
        compose.waitUntil(30_000) {
            compose.waitForIdle()
            vm.ui.value.let { it.ready && !it.busy } && records.ui.value.ready &&
                ViewModelProvider(compose.activity)[PlansViewModel::class.java].ui.value.ready &&
                ViewModelProvider(compose.activity)[ToolsViewModel::class.java].ui.value.ready
        }
        compose.waitForIdle()
    }

    private fun action(block: () -> Unit) {
        compose.runOnIdle(block)
        ready()
        assertNull(vm.ui.value.error, vm.ui.value.error)
    }

    private fun begin(kind: String = "expense") {
        action { vm.begin(kind) }
        assertNotNull(vm.ui.value.editor)
    }

    private fun discard() {
        action { vm.discardEditor() }
        assertNull(vm.ui.value.editor)
    }

    private fun ledger() {
        ready()
        compose.onNodeWithTag("root:账本").performClick()
        ready()
    }

    private fun tab(id: String) {
        val node = compose.onNodeWithTag("ledgerTab:$id")
        if (compose.activity.resources.configuration.fontScale > 1.3f) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val workspace = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "AGENTS.md").isFile && File(it, "daily-life-android").isDirectory }
            val directory = File(workspace, "diagnostics/ledger-experience-evidence/screens").apply { mkdirs() }
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                ?: compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private data class BrowseFixture(val food: String, val bank: String, val lunch: String, val shopping: String)

    private fun seedBrowsing(): BrowseFixture {
        val fixture = runBlocking {
            val snapshot = app.ledger.load()
            val book = snapshot.defaultLedger()!!.id
            app.ledger.saveAccount("日常银行卡", "bank", "5200.00", "2020-01-01", tail = "1026")
            val bank = app.ledger.load().accounts.single { it.name == "日常银行卡" }.id
            val food = snapshot.categories.single { it.name == "餐饮" && it.direction == "expense" }.id
            val subjects = mutableMapOf<String, String>()
            listOf(Triple("午餐", "餐饮", "32.00"), Triple("地铁通勤", "交通", "6.00"),
                Triple("生活用品", "购物", "86.50")).forEach { (subject, category, amount) ->
                val form = LedgerForm().set("ledger", book).set("date", if (subject == "生活用品") "2024-02-02" else "2024-02-01")
                    .set("amount", amount).set("subject", subject).set("account", bank).set("channel", "微信")
                    .set("category", snapshot.categories.single { it.name == category && it.direction == "expense" }.id)
                app.ledger.save(form)
                subjects[subject] = form.id
            }
            app.ledger.save(LedgerForm("income").set("ledger", book).set("date", "2024-02-02")
                .set("amount", "8000.00").set("subject", "本月工资").set("account", bank)
                .set("category", snapshot.categories.single { it.name == "工资" && it.direction == "income" }.id))
            BrowseFixture(food, bank, subjects.getValue("午餐"), subjects.getValue("生活用品"))
        }
        compose.waitUntil(15_000) { compose.waitForIdle(); vm.ui.value.data.transactions.size == 4 }
        action { vm.day("2024-02-01"); vm.periodMode("month") }
        return fixture
    }

    @Before fun setup() {
        ready()
        runBlocking {
            app.preferences.set(Appearance(theme = "light", reducedMotion = true))
            app.preferences.setDefaultPaymentChannel("")
        }
        compose.waitUntil(15_000) {
            compose.waitForIdle()
            records.ui.value.appearance.theme == "light" && vm.ui.value.defaultPaymentChannel.isBlank()
        }
    }

    @After fun close() {
        compose.activity.finish()
        app.database.close()
        app.backgroundScope.cancel()
    }

    @Test fun periodSteppingHandlesLeapMonthsAndYearsWithoutDroppingSearchOrAccounts() {
        val filter = LedgerTransactionFilter(query = "午餐", type = "expense", category = "food", account = "bank",
            from = "2001-01-01", to = "2001-01-02")
        action { vm.filter(filter); vm.day("2024-01-31"); vm.periodMode("month"); vm.stepPeriod(1) }
        assertEquals("2024-02-29", vm.ui.value.selectedDate)
        assertEquals("2024-02", vm.ui.value.month)
        assertEquals(filter.copy(from = "", to = ""), vm.ui.value.transactionFilter)
        action { vm.stepPeriod(12) }
        assertEquals("2025-02-28", vm.ui.value.selectedDate)
        action { vm.day("2024-12-31"); vm.stepPeriod(1) }
        assertEquals("2025-01-01", vm.ui.value.selectedDate)
        assertEquals("day", vm.ui.value.periodMode)
        assertEquals("2025-01", vm.ui.value.month)
        action { vm.stepPeriod(-1) }
        assertEquals("2024-12-31", vm.ui.value.selectedDate)
        action { vm.month("2024-02") }
        assertEquals("2024-02-29", vm.ui.value.selectedDate)
        assertEquals("month", vm.ui.value.periodMode)
    }

    @Test fun paymentDefaultAppliesOnlyToNewOrdinaryEntriesAndCanBeCleared() {
        action { vm.setDefaultPaymentChannel("微信") }
        assertEquals("微信", runBlocking { app.preferences.defaultPaymentChannel.first() })
        listOf("expense", "income").forEach { kind ->
            begin(kind)
            assertEquals("微信", vm.ui.value.editor!!.text("channel"))
            assertEquals("", vm.ui.value.editor!!.text("account"))
            discard()
        }
        listOf("transfer", "refund", "reimbursement").forEach { kind ->
            begin(kind)
            assertEquals("", vm.ui.value.editor!!.text("channel"))
            discard()
        }
        action { vm.setDefaultPaymentChannel("") }
        assertEquals("", runBlocking { app.preferences.defaultPaymentChannel.first() })
        begin()
        assertEquals("", vm.ui.value.editor!!.text("channel"))
        discard()
        assertTrue(runBlocking { app.ledger.load() }.transactions.isEmpty())
        assertTrue(runBlocking { app.ledger.load() }.entries.isEmpty())
    }

    @Test fun changingDefaultDoesNotRewriteEditingRestoredCopiedOrTemplateChannels() {
        val existing = runBlocking {
            val book = app.ledger.load().defaultLedger()!!.id
            val form = LedgerForm().set("ledger", book).set("date", LocalDate.now().toString())
                .set("amount", "12.34").set("subject", "原始账目").set("channel", "支付宝")
            app.ledger.save(form)
            app.ledger.load().transactions.single { it.id == form.id }
        }
        action { vm.setDefaultPaymentChannel("微信") }
        action { vm.begin(id = existing.id) }
        assertEquals("支付宝", vm.ui.value.editor!!.text("channel"))
        action { vm.setDefaultPaymentChannel("现金") }
        assertEquals("支付宝", vm.ui.value.editor!!.text("channel"))
        discard()
        action { vm.duplicate(existing) }
        assertEquals("支付宝", vm.ui.value.editor!!.text("channel"))
        discard()
        val template = runBlocking {
            TransactionTemplate(ownerId = app.records.owner(), name = "无指定渠道模板",
                payload = app.ledger.form(existing.id).set("channel", "").json())
        }
        action { vm.begin(template = template) }
        assertEquals("", vm.ui.value.editor!!.text("channel"))
        assertNotEquals(existing.id, vm.ui.value.editor!!.id)
        discard()
        val recovery = runBlocking {
            val form = LedgerForm().set("ledger", existing.ledgerId).set("date", LocalDate.now().toString())
                .set("amount", "9.50").set("channel", "")
            app.ledger.prepareDraft(form)
            app.ledger.load().drafts.single { it.id == form.id }
        }
        action { vm.resume(recovery) }
        assertEquals("", vm.ui.value.editor!!.text("channel"))
        discard()
        assertEquals(existing, runBlocking { app.ledger.load() }.transactions.single { it.id == existing.id })
    }

    @Test fun saveAndContinueKeepsExplicitNoChannelOnSavedEntryAndUsesDefaultForNextEntry() {
        action { vm.setDefaultPaymentChannel("微信") }
        begin()
        val draft = vm.ui.value.editor!!.set("amount", "28.50").set("subject", "本次不填渠道").set("channel", "")
        action { vm.edit(draft); vm.save(keepAdding = true) }
        val saved = runBlocking { app.ledger.load() }.transactions.single { it.id == draft.id }
        assertEquals(2850L, saved.amountMinor)
        assertEquals("", saved.paymentChannel)
        assertNull(saved.accountId)
        assertTrue(runBlocking { app.ledger.load() }.entries.isEmpty())
        assertNotEquals(draft.id, vm.ui.value.editor!!.id)
        assertEquals("微信", vm.ui.value.editor!!.text("channel"))
        assertEquals("", vm.ui.value.editor!!.text("amount"))
        discard()
        assertEquals(1, runBlocking { app.ledger.load() }.transactions.size)
    }

    @Test fun discardingCorrectionCancelsPendingDraftAndPreservesOriginalEntriesAndSharedAttachment() {
        val fixture = runBlocking {
            val book = app.ledger.load().defaultLedger()!!.id
            app.ledger.saveAccount("原银行卡", "bank", "1000", LocalDate.now().minusYears(1).toString())
            val bank = app.ledger.load().accounts.single().id
            val form = LedgerForm().set("ledger", book).set("date", LocalDate.now().toString())
                .set("amount", "20.00").set("subject", "保留的原账目").set("account", bank)
            app.ledger.prepareDraft(form)
            val bytes = "原始记账凭证".toByteArray(Charsets.UTF_8)
            val fileId = newId()
            val file = File(app.records.files.root, "$fileId.bin").apply { writeBytes(bytes) }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val attachment = Attachment(fileId, app.records.owner(), "凭证.txt", "凭证.txt", "text/plain",
                bytes.size.toLong(), hash, "attachments/$fileId.bin", System.currentTimeMillis())
            app.records.dao.putAttachment(attachment)
            app.records.dao.putLinks(listOf(AttachmentLink("ledger_draft", form.id, fileId)))
            app.ledger.save(form)
            val unrelated = LedgerForm().set("ledger", book).set("subject", "旧版待恢复内容")
            app.ledger.prepareDraft(unrelated)
            Triple(app.ledger.load(), attachment, file)
        }
        val before = fixture.first
        val original = before.transactions.single()
        action { vm.begin(id = original.id) }
        action { vm.run(null) { removeAttachment(original.id, fixture.second.id) } }
        action {
            vm.edit(vm.ui.value.editor!!.set("amount", "999.99").set("subject", "这次修改应放弃"))
            vm.discardEditor()
        }
        assertNull(vm.ui.value.editor)
        val after = runBlocking { app.ledger.load() }
        assertEquals(original, after.transactions.single())
        assertEquals(before.entries, after.entries)
        val accountId = requireNotNull(original.accountId)
        assertEquals(before.balance(accountId), after.balance(accountId))
        assertEquals(before.changes, after.changes)
        assertEquals(before.drafts, after.drafts)
        assertTrue(runBlocking { app.records.dao.linksFor("ledger_draft", original.id) }.isEmpty())
        assertEquals(listOf(fixture.second.id), runBlocking { app.records.dao.linksFor("transaction", original.id) }.map { it.attachmentId })
        assertTrue(fixture.third.exists())
    }

    @Test fun dayMonthTotalsAndTrendDrilldownMatchTheVisibleDateWithoutObsoleteTopActions() {
        val fixture = seedBrowsing()
        ledger()
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 124.50")
        listOf("ledgerTabIndicator", "hideLedgerAmounts", "ledgerMore", "ledgerMonth", "chooseLedgerCsvTarget")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.onAllNodes(hasText("导出", substring = true)).assertCountEquals(0)
        shot("01-overview-light")

        compose.onNodeWithTag("ledgerDateRange").performClick()
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:2024-02-01"))
        compose.onNodeWithTag("calendarDay:2024-02-01").performClick()
        compose.onNodeWithTag("confirmDate").assertIsDisplayed().performClick()
        assertEquals("day", vm.ui.value.periodMode)
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 38.00")
        compose.onNodeWithTag("ledgerPeriod:month").performClick()
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 124.50")

        tab("stats")
        compose.onNodeWithTag("ledgerStatsTotal").assertTextEquals("¥ 124.50")
        shot("02-statistics-light")
        compose.onNodeWithTag("ledgerStatsDirection").performClick()
        compose.onNodeWithTag("ledgerStatsDirection:income").performClick()
        compose.onNodeWithTag("ledgerStatsTotal").assertTextEquals("¥ 8000.00")
        compose.onNodeWithTag("ledgerPeriod:day").performClick()
        compose.onNodeWithTag("ledgerStatsTotal").assertTextEquals("¥ 0.00")
        compose.onNodeWithTag("ledgerPeriod:month").performClick()
        compose.onNodeWithTag("ledgerStatsDirection").performClick()
        compose.onNodeWithTag("ledgerStatsDirection:expense").performClick()
        action { vm.filter(LedgerTransactionFilter(query = "无匹配旧搜索", from = "2000-01-01", to = "2000-01-02", min = "999")) }
        compose.onNodeWithTag("ledgerStatistics").performScrollToNode(hasTestTag("ledgerDailyTrend"))
        compose.onNodeWithTag("ledgerDailyTrend").performScrollToNode(hasTestTag("ledgerTrendDay:2024-02-02"))
        compose.onNodeWithTag("ledgerTrendDay:2024-02-02").performClick()
        assertEquals("transactions", vm.ui.value.page)
        assertEquals("2024-02-02", vm.ui.value.selectedDate)
        assertEquals("day", vm.ui.value.periodMode)
        assertEquals(LedgerTransactionFilter(type = "expense"), vm.ui.value.transactionFilter)
        compose.onNodeWithTag("ledgerTransactions").performScrollToNode(hasTestTag("ledgerTransaction:${fixture.shopping}"))
        compose.onNodeWithTag("ledgerTransaction:${fixture.shopping}").assertIsDisplayed()
        compose.onNodeWithTag("ledgerTransaction:${fixture.lunch}").assertDoesNotExist()
        compose.onNodeWithTag("ledgerTransactions").performScrollToIndex(0)
        shot("03-details-light")

        runBlocking {
            app.preferences.set(Appearance(theme = "dark", reducedMotion = true))
            app.preferences.setDashboard(app.preferences.dashboard.first().copy(hideAmounts = true))
        }
        compose.waitUntil(15_000) {
            compose.waitForIdle()
            records.ui.value.appearance.theme == "dark" &&
                ViewModelProvider(compose.activity)[HomeViewModel::class.java].state.value.hideAmounts
        }
        action { vm.month("2024-02") }
        tab("overview")
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 124.50")
        assertTrue(runBlocking { app.preferences.dashboard.first() }.hideAmounts)
        shot("05-overview-dark")
        tab("stats")
        compose.onNodeWithTag("ledgerStatistics").performScrollToIndex(0)
        compose.onNodeWithTag("ledgerStatsTotal").assertTextEquals("¥ 124.50")
        shot("06-statistics-dark")
    }

    @Test fun dropdownAndAdvancedFiltersOnlyCommitWhenApplied() {
        val fixture = seedBrowsing()
        ledger()
        tab("transactions")
        compose.onNodeWithTag("ledgerTypeFilter").performClick()
        compose.onNodeWithTag("ledgerFilterType:expense").performClick()
        compose.onNodeWithTag("ledgerCategoryFilter").performClick()
        compose.onNodeWithTag("selectionOptions").performScrollToNode(hasTestTag("selectionOption:${fixture.food}"))
        compose.onNodeWithTag("selectionOption:${fixture.food}").performClick()
        compose.onNodeWithTag("ledgerSearch").performTextReplacement("午餐")
        val before = vm.ui.value.transactionFilter
        assertEquals(LedgerTransactionFilter(query = "午餐", type = "expense", category = fixture.food), before)

        compose.onNodeWithTag("ledgerAdvancedFilter").performClick()
        compose.onNode(hasText("账户") and hasText("全部账户")).performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:${fixture.bank}").performClick()
        compose.onNode(hasSetTextAction() and hasText("最低金额（选填）")).performScrollTo().performTextReplacement("100")
        assertEquals(before, vm.ui.value.transactionFilter)
        compose.onNodeWithText("关闭", substring = false).performClick()
        compose.waitForIdle()
        assertEquals(before, vm.ui.value.transactionFilter)
        compose.onNodeWithTag("ledgerTransactions").performScrollToNode(hasTestTag("ledgerTransaction:${fixture.lunch}"))
        compose.onNodeWithTag("ledgerTransaction:${fixture.lunch}").assertIsDisplayed()

        compose.onNodeWithTag("ledgerAdvancedFilter").performClick()
        compose.onNode(hasText("账户") and hasText("全部账户")).performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:${fixture.bank}").performClick()
        compose.onNode(hasSetTextAction() and hasText("最低金额（选填）")).performScrollTo().performTextReplacement("30")
        compose.onNode(hasSetTextAction() and hasText("最高金额（选填）")).performScrollTo().performTextReplacement("35")
        compose.onNodeWithTag("applyLedgerFilter").assertIsDisplayed()
        shot("04-filter-sheet-light")
        compose.onNodeWithTag("applyLedgerFilter").performClick()
        assertEquals(before.copy(account = fixture.bank, min = "30", max = "35"), vm.ui.value.transactionFilter)
        compose.onNodeWithTag("ledgerTransactions").performScrollToIndex(0)
        compose.onNodeWithTag("ledgerActiveFilters").assertTextContains("日常银行卡", substring = true)
        compose.onNodeWithTag("clearLedgerFilters").performClick()
        assertEquals(LedgerTransactionFilter(), vm.ui.value.transactionFilter)
    }

    @Test fun calendarMonthBrowsingIsTemporaryAndViewWholeMonthUsesTheVisibleMonth() {
        seedBrowsing()
        ledger()
        compose.onNodeWithTag("ledgerDateRange").performClick()
        compose.onNodeWithTag("calendarPrevious").performClick()
        assertEquals("2024-02", vm.ui.value.month)
        compose.onNodeWithTag("cancelDate").performClick()
        assertEquals("2024-02", vm.ui.value.month)
        compose.onNodeWithTag("ledgerDateRange").performClick()
        compose.onNodeWithTag("calendarPrevious").performClick()
        compose.onNodeWithTag("calendarChooseMonth").assertIsDisplayed().performClick()
        assertEquals("2024-01", vm.ui.value.month)
        assertEquals("month", vm.ui.value.periodMode)
        compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 0.00")
    }

    @Test @Config(qualifiers = "w320dp-h700dp-xxhdpi")
    fun twoTimesFontKeepsNavigationFiltersCalendarsAndCategoryActionsReachable() {
        compose.runOnIdle {
            val config = android.content.res.Configuration(compose.activity.resources.configuration)
            config.fontScale = 2f
            compose.activity.resources.updateConfiguration(config, compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        ready()
        seedBrowsing()
        ledger()
        listOf("overview", "transactions", "stats", "accounts").forEach { id ->
            tab(id)
            compose.onNodeWithTag("ledgerTab:$id").assertIsSelected().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        tab("overview")
        compose.onNodeWithTag("addLedgerTransaction").assertIsDisplayed().assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        val contentBottom = compose.onNodeWithTag("ledgerContent").fetchSemanticsNode().boundsInRoot.bottom
        val actionTop = compose.onNodeWithTag("addLedgerTransaction").fetchSemanticsNode().boundsInRoot.top
        assertTrue("新增按钮不得覆盖正文", contentBottom <= actionTop)
        shot("08-overview-font2")
        tab("transactions")
        compose.onNodeWithTag("ledgerDateRange").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        listOf("ledgerTypeFilter", "ledgerCategoryFilter", "ledgerAdvancedFilter").forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("ledgerTypeFilter").performScrollTo()
        compose.onNodeWithTag("ledgerSearch").assertIsDisplayed()
        shot("09-details-font2")
        compose.onNodeWithTag("ledgerDateRange").performClick()
        compose.onNodeWithTag("calendarMode:grid").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("calendarMode:list").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("confirmDate").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("cancelDate").assertIsDisplayed()
        shot("10-date-list-font2")
        compose.onNodeWithTag("calendarMode:grid").performScrollTo().performClick()
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:2024-02-05"))
        compose.onNodeWithTag("calendarDay:2024-02-05").performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        shot("11-date-grid-font2")
        compose.onNodeWithTag("confirmDate").assertIsDisplayed().performClick()
        assertEquals("2024-02-05", vm.ui.value.selectedDate)
        assertEquals("day", vm.ui.value.periodMode)
        compose.onNodeWithTag("addLedgerTransaction").performClick()
        ready()
        compose.onNodeWithTag("ledgerAmount").performTextReplacement("28.50")
        shot("12-editor-font2")
        compose.onNodeWithTag("ledgerCategorySelect").performScrollTo().performClick()
        compose.onNodeWithTag("ledgerCategoryGrid").assertIsDisplayed()
        compose.onNodeWithTag("addLedgerCategory").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        shot("13-category-font2")
        compose.onNodeWithText("关闭", substring = false).performClick()
        compose.onNodeWithTag("cancelLedgerEditor").assertIsDisplayed().performClick()
        ready()
        assertNull(vm.ui.value.editor)
        assertEquals(4, runBlocking { app.ledger.load() }.transactions.size)
    }
}
