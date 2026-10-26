package com.example.dailylife
import com.example.dailylife.core.ToolRules
import org.junit.Test
import org.junit.Assert.*
class ToolRulesTest {
    @Test fun calculatorHasArithmeticPrecedenceAndExplicitPercent() {assertEquals("12",ToolRules.calculate("(20 + 4) × 50%").toPlainString());assertEquals("7",ToolRules.calculate("1+2*3").toPlainString());assertEquals("-2",ToolRules.calculate("-(1+1)").toPlainString())}
    @Test fun calculatorRejectsUnsafeOrIncompleteInput() {listOf("1/0","1+","(2+3","alert(1)","1..2").forEach {assertTrue(it,runCatching {ToolRules.calculate(it)}.isFailure)}}
    @Test fun aaPreservesEveryCentAndRemainderIsDeterministic() {assertEquals(listOf(3334L,3333L,3333L),ToolRules.aa("100","3"));assertEquals(listOf(1L,0L,0L),ToolRules.aa("0.01","3"));assertEquals(10000L,ToolRules.aa("100","999").sum())}
    @Test fun quantityAndMoneyRejectExtraPrecisionInsteadOfRounding() {assertEquals(1234L,ToolRules.milli("1.234"));assertEquals(123L,ToolRules.minor("1.23"));assertTrue(runCatching {ToolRules.milli("1.2345")}.isFailure);assertTrue(runCatching {ToolRules.minor("1.234")}.isFailure);assertTrue(runCatching {ToolRules.aa("1","0")}.isFailure)}
}
