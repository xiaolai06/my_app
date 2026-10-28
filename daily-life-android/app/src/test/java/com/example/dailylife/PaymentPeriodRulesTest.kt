package com.example.dailylife

import com.example.dailylife.core.PaymentPeriodRules
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PaymentPeriodRulesTest {
    @Test fun january31KeepsMarchAnchorAndHasNoFebruaryGap() {
        val anchor=LocalDate.parse("2025-01-31")
        assertEquals(LocalDate.parse("2025-02-28"),PaymentPeriodRules.start(anchor,"monthly",1,1))
        assertEquals(LocalDate.parse("2025-03-30"),PaymentPeriodRules.end(anchor,"monthly",1,1))
        assertEquals(LocalDate.parse("2025-03-31"),PaymentPeriodRules.start(anchor,"monthly",1,2))
    }
    @Test fun leapYearAnchorReturnsToFebruary29() {
        val anchor=LocalDate.parse("2024-02-29")
        assertEquals(LocalDate.parse("2028-02-29"),PaymentPeriodRules.start(anchor,"yearly",1,4))
        assertEquals(LocalDate.parse("2028-02-28"),PaymentPeriodRules.end(anchor,"yearly",1,3))
    }
    @Test fun allSupportedIntervalsStayContiguousAcrossSeveralYears() {
        for(day in listOf("2024-01-31","2024-02-29","2025-03-30"))
            for(cycle in listOf("daily","weekly","monthly","yearly"))
                for(interval in listOf(1,2,3,12)) for(i in 0..48) {
                    val anchor=LocalDate.parse(day)
                    val end=PaymentPeriodRules.end(anchor,cycle,interval,i)
                    assertEquals(PaymentPeriodRules.start(anchor,cycle,interval,i+1),end.plusDays(1))
                    assertFalse(end.isBefore(PaymentPeriodRules.start(anchor,cycle,interval,i)))
                }
    }
}
