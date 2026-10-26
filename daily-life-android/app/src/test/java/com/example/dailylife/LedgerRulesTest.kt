package com.example.dailylife
import com.example.dailylife.core.LedgerRules
import org.junit.Test
import org.junit.Assert.*
class LedgerRulesTest {
    @Test fun moneyIsExactIntegerCents() {assertEquals(28L,LedgerRules.minor("0.28"));assertEquals(10010L,LedgerRules.minor("100.10"));assertEquals("-0.01",LedgerRules.display(-1))}
    @Test fun rejectsRoundingAndScientificNotation() {listOf("1.001","1e3","NaN","-1","0","").forEach {assertTrue(it,runCatching {LedgerRules.minor(it)}.isFailure)}}
    @Test fun allowsSignedOpeningOnlyExplicitly() {assertEquals(-100L,LedgerRules.minor("-1",signed=true));assertEquals(0L,LedgerRules.minor("0",allowZero=true));assertTrue(runCatching {LedgerRules.minor("1000000000")}.isFailure)}
    @Test fun csvSupportsBomQuotesCommaAndNewline() {val rows=LedgerRules.csv("\uFEFF日期,备注\r\n2026-01-01,\"午餐,咖啡\n第二行\"\r\n");assertEquals(2,rows.size);assertEquals("午餐,咖啡\n第二行",rows[1][1])}
    @Test fun csvEscapesQuotesAndRejectsMalformed() {assertEquals("a\"b",LedgerRules.csv("\"a\"\"b\"")[0][0]);assertTrue(runCatching {LedgerRules.csv("\"abc")}.isFailure);assertTrue(runCatching {LedgerRules.csv("\"abc\"x")}.isFailure)}
    @Test fun csvExportRoundTripsLiteralValues() {val input=listOf("正常","a,b","多\n行","\"引号\"","");assertEquals(input,LedgerRules.csv(input.joinToString(",",transform=LedgerRules::csvCell))[0])}
}
