package com.example.dailylife.core

import org.junit.Assert.*
import org.junit.Test

class RecordRulesTest {
    @Test fun titleAloneCannotCreateEmptyRecord() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.content("只有标题", "  \n", 0) }
        RecordRules.content("", "中文内容", 0)
        RecordRules.content("", "", 1)
    }
    @Test fun normalizationDeduplicatesFullWidthAndCase() {
        assertEquals(RecordRules.normalized("生活 ＡＢＣ"), RecordRules.normalized("生活 abc"))
        assertEquals("读书 笔记", RecordRules.normalized(" 读书   笔记 "))
    }
    @Test fun urlPreservesMeaningfulQueryAndFragment() {
        assertEquals("https://example.com/a?id=12&mode=full#part", RecordRules.url("example.com/a?id=12&mode=full#part"))
        assertEquals("https://example.com/?next=https://other.example/path",
            RecordRules.url("example.com/?next=https://other.example/path"))
        assertEquals("https://example.com:8080/path", RecordRules.url("example.com:8080/path"))
        assertEquals("https://example.com/%E4%B8%AD%E6%96%87?q=%E6%97%A5%E5%B8%B8",
            RecordRules.url("https://example.com/中文?q=日常"))
    }
    @Test fun unsupportedLinksNeverBecomeExecutable() {
        listOf("javascript:alert(1)", "file:///sdcard/a", "https://user:secret@example.com/a", "data:text/html,test", "", "https://").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { RecordRules.url(it) }
        }
    }
    @Test fun diaryDateChecksRealCalendar() {
        assertEquals("2024-02-29", RecordRules.diaryDate("2024-02-29"))
        assertThrows(IllegalArgumentException::class.java) { RecordRules.diaryDate("2025-02-29") }
    }
    @Test fun limitsRejectRatherThanSilentlyTruncateData() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.content("字".repeat(121), "正文", 0) }
        assertThrows(IllegalArgumentException::class.java) { RecordRules.content("", "字".repeat(50001), 0) }
    }
}
