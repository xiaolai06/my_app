package com.example.dailylife

import com.example.dailylife.data.local.LedgerCategory
import com.example.dailylife.data.local.LedgerDraft
import com.example.dailylife.data.local.LedgerTransaction
import com.example.dailylife.feature.ledger.*
import org.junit.Assert.*
import org.junit.Test

class LedgerBrowsingTest {
    private fun row(
        id: String,
        type: String = "expense",
        amount: Long = 100,
        date: String = "2024-02-15",
        book: String = "book",
        category: String? = "food",
        account: String? = null,
        toAccount: String? = null,
        deleted: Boolean = false,
    ) = LedgerTransaction(id, "owner", book, type, amount, date, categoryId = category,
        accountId = account, toAccountId = toAccount, createdAt = 1, deletedAt = if (deleted) 2 else null)

    @Test fun periodsUseCalendarBoundsIncludingLeapDaysAndYearEnds() {
        assertEquals("2024-02-01", LedgerPeriod("month", "2024-02-29").from)
        assertEquals("2024-02-29", LedgerPeriod("month", "2024-02-01").to)
        assertEquals("2023-02-28", LedgerPeriod("month", "2023-02-14").to)
        assertEquals("2025-12-31", LedgerPeriod("month", "2025-12-02").to)
        val day = LedgerPeriod("day", "2024-02-29")
        assertEquals("2024-02-29", day.from)
        assertEquals(day.from, day.to)
    }

    @Test fun periodRowsIncludeBothBoundsButExcludeOtherBooksDeletedRowsAndDrafts() {
        val snapshot = LedgerSnapshot(
            transactions = listOf(row("before", date = "2024-01-31"), row("first", date = "2024-02-01"),
                row("last", date = "2024-02-29"), row("after", date = "2024-03-01"),
                row("other", book = "other-book"), row("deleted", deleted = true)),
            drafts = listOf(LedgerDraft("draft", "owner", "{}")),
        )
        val month = LedgerPeriod("month", "2024-02-20")
        assertEquals(listOf("first", "last"), snapshot.periodRows("book", month).map { it.id })
        assertEquals(setOf("first", "last", "other"), snapshot.periodRows(null, month).map { it.id }.toSet())
        assertEquals(listOf("last"), snapshot.periodRows("book", LedgerPeriod("day", "2024-02-29")).map { it.id })
    }

    @Test fun summaryKeepsRefundsAndReimbursementsSeparateFromIncomeAndTransfer() {
        val snapshot = LedgerSnapshot(transactions = listOf(
            row("original", amount = 10000, date = "2024-01-31"),
            row("expense", amount = 2000), row("unassigned", amount = 300),
            row("fee", amount = 100).copy(relatedTransactionId = "transfer"),
            row("income", "income", 5000), row("refund", "refund", 700).copy(originalExpenseId = "original"),
            row("reimbursement", "reimbursement", 800), row("transfer", "transfer", 99900, account = "a", toAccount = "b"),
            row("adjustment", "adjustment", -1000), row("deleted", "income", 100000, deleted = true),
        ))
        val summary = snapshot.periodRows("book", LedgerPeriod("month", "2024-02-10")).ledgerSummary()
        assertEquals(2400L, summary.expense)
        assertEquals(5000L, summary.income)
        assertEquals(700L, summary.refunds)
        assertEquals(800L, summary.reimbursement)
        assertEquals(1700L, summary.netExpense)
        assertEquals(10000L, snapshot.periodRows("book", LedgerPeriod("month", "2024-01-31")).ledgerSummary().netExpense)
    }

    @Test fun monthlyTrendFillsEveryCalendarDayAndDoesNotCountRefundsAsIncome() {
        val snapshot = LedgerSnapshot(transactions = listOf(
            row("first", amount = 100, date = "2024-02-01"), row("same-day", amount = 50, date = "2024-02-01"),
            row("leap", amount = 200, date = "2024-02-29"), row("deleted", amount = 800, deleted = true),
            row("other-book", amount = 900, book = "other"), row("income", "income", 300),
            row("refund", "refund", 1000), row("reimbursement", "reimbursement", 2000),
        ))
        val period = LedgerPeriod("month", "2024-02-29")
        val expense = snapshot.dailyTrend("book", period, "expense")
        assertEquals(29, expense.size)
        assertEquals(LedgerDayTotal("2024-02-01", 150), expense.first())
        assertEquals(LedgerDayTotal("2024-02-29", 200), expense.last())
        assertTrue(expense.subList(1, 28).all { it.amount == 0L })
        assertEquals(350L, expense.sumOf { it.amount })
        assertEquals(300L, snapshot.dailyTrend("book", period, "income").sumOf { it.amount })
        val empty = snapshot.dailyTrend("book", LedgerPeriod("month", "2023-02-10"), "expense")
        assertEquals(28, empty.size)
        assertTrue(empty.all { it.amount == 0L })
    }

    @Test fun dayTrendHasSevenDaysAcrossYearBoundaryWithoutChangingSelectedDayTotal() {
        val snapshot = LedgerSnapshot(transactions = listOf(
            row("outside-before", date = "2024-12-26"), row("start", amount = 200, date = "2024-12-27"),
            row("selected", amount = 300, date = "2025-01-02"), row("outside-after", date = "2025-01-03"),
        ))
        val period = LedgerPeriod("day", "2025-01-02")
        val trend = snapshot.dailyTrend("book", period, "expense")
        assertEquals(7, trend.size)
        assertEquals(LedgerDayTotal("2024-12-27", 200), trend.first())
        assertEquals(LedgerDayTotal("2025-01-02", 300), trend.last())
        assertEquals(500L, trend.sumOf { it.amount })
        assertEquals(300L, snapshot.periodRows("book", period).ledgerSummary().expense)
        assertThrows(IllegalArgumentException::class.java) { snapshot.dailyTrend("book", period, "transfer") }
    }

    @Test fun accountFilterFindsBothSidesOfTransfersWithoutIncludingUnassignedRows() {
        val snapshot = LedgerSnapshot(transactions = listOf(
            row("expense", account = "a"), row("outgoing", "transfer", account = "a", toAccount = "b"),
            row("incoming", "transfer", account = "b", toAccount = "a"),
            row("other", account = "b"), row("unassigned"),
        ))
        val period = LedgerPeriod("month", "2024-02-15")
        assertEquals(setOf("expense", "outgoing", "incoming"),
            snapshot.browseRows("book", period, LedgerTransactionFilter(account = "a")).map { it.id }.toSet())
        assertEquals(listOf("expense"), snapshot.browseRows("book", period,
            LedgerTransactionFilter(type = "expense", category = "food", account = "a")).map { it.id })
    }

    @Test fun unclassifiedDrilldownDoesNotAccidentallyMeanAllCategories() {
        val snapshot = LedgerSnapshot(transactions = listOf(row("null", category = null), row("blank", category = ""),
            row("food"), row("protected-category", category = "uncategorized-id")))
        val period = LedgerPeriod("month", "2024-02-15")
        assertEquals(setOf("null", "blank"), snapshot.browseRows("book", period,
            LedgerTransactionFilter(category = UnclassifiedLedgerCategory)).map { it.id }.toSet())
        assertEquals(listOf("protected-category"), snapshot.browseRows("book", period,
            LedgerTransactionFilter(category = "uncategorized-id")).map { it.id })
        assertEquals(4, snapshot.browseRows("book", period, LedgerTransactionFilter()).size)
    }

    @Test fun parentCategorySelectionAndPathSearchIncludeItsChildrenWithoutMatchingOtherParents() {
        val snapshot = LedgerSnapshot(
            categories = listOf(
                LedgerCategory("food", "owner", "餐饮", "expense", uniqueKey = "expense|food"),
                LedgerCategory("lunch", "owner", "午餐", "expense", parentId = "food", uniqueKey = "expense|food|lunch"),
                LedgerCategory("coffee", "owner", "咖啡", "expense", parentId = "food", uniqueKey = "expense|food|coffee"),
                LedgerCategory("transport", "owner", "交通", "expense", uniqueKey = "expense|transport"),
                LedgerCategory("metro", "owner", "地铁", "expense", parentId = "transport", uniqueKey = "expense|transport|metro"),
            ),
            transactions = listOf(row("legacy-parent", category = "food"), row("lunch", category = "lunch"),
                row("coffee", category = "coffee"), row("metro", category = "metro")),
        )
        val period = LedgerPeriod("month", "2024-02-15")
        val family = setOf("legacy-parent", "lunch", "coffee")
        assertEquals(family, snapshot.browseRows("book", period, LedgerTransactionFilter(category = "food")).map { it.id }.toSet())
        assertEquals(family, snapshot.browseRows("book", period, LedgerTransactionFilter(query = "餐饮")).map { it.id }.toSet())
        assertEquals(listOf("lunch"), snapshot.browseRows("book", period, LedgerTransactionFilter(query = "餐饮 / 午餐")).map { it.id })
        assertEquals(listOf("coffee"), snapshot.browseRows("book", period, LedgerTransactionFilter(category = "coffee")).map { it.id })
    }

    @Test fun combinedSearchAndAmountFiltersUseExactCentsAndExplicitRangeOverridesPeriod() {
        val snapshot = LedgerSnapshot(
            categories = listOf(LedgerCategory("food", "owner", "餐饮", "expense", uniqueKey = "expense|food")),
            transactions = listOf(row("jan", amount = 99, date = "2024-01-31").copy(subject = "Coffee"),
                row("feb", amount = 100, date = "2024-02-01").copy(note = "COFFEE"),
                row("march", amount = 101, date = "2024-03-01").copy(counterparty = "coffee"),
                row("unbudgeted", amount = 100, date = "2024-02-01").copy(subject = "coffee", includeInBudget = false)),
        )
        val period = LedgerPeriod("day", "2024-02-15")
        val filter = LedgerTransactionFilter(query = "  coffee  ", from = "2024-01-31", to = "2024-03-01",
            min = "1.00", max = "1.00", onlyBudget = true)
        val rows = snapshot.browseRows("book", period, filter)
        assertEquals(listOf("feb"), rows.map { it.id })
        assertEquals(100L, rows.ledgerSummary().expense)
        assertEquals(4, snapshot.browseRows("book", period, LedgerTransactionFilter(query = "餐饮", from = "2024-01-01")).size)
        assertEquals(listOf("jan"), snapshot.browseRows("book", period, LedgerTransactionFilter(to = "2024-01-31")).map { it.id })
    }

    @Test fun invalidLegacyDateOrAmountRangesReturnNoMisleadingMatches() {
        val snapshot = LedgerSnapshot(transactions = listOf(row("row")))
        val period = LedgerPeriod("month", "2024-02-15")
        val invalid = listOf(LedgerTransactionFilter(from = "2024-02-30"),
            LedgerTransactionFilter(from = "2024-03-01", to = "2024-02-01"),
            LedgerTransactionFilter(min = "1.001"), LedgerTransactionFilter(max = "NaN"),
            LedgerTransactionFilter(min = "2", max = "1"))
        invalid.forEach { assertTrue(it.toString(), snapshot.browseRows("book", period, it).isEmpty()) }
    }
}
