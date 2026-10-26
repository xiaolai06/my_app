package com.example.dailylife.core

import java.net.URI
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

object RecordRules {
    const val MAX_BODY = 50_000
    const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    const val MAX_FILE_BYTES = 100L * 1024 * 1024
    fun normalized(name: String): String =
        Normalizer.normalize(name.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    fun name(name: String, max: Int = 32): String {
        val result = name.trim().replace(Regex("\\s+"), " ")
        require(result.isNotEmpty() && result.length <= max) { "名称需要 1～" + max + " 个字符" }
        return result
    }
    fun url(value: String): String {
        val raw = value.trim()
        require(raw.isNotEmpty()) { "请填写链接" }
        val withScheme = if (Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(raw)) raw else "https://" + raw
        val uri = try { URI(withScheme) } catch (_: Exception) { throw IllegalArgumentException("链接格式不正确") }
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null) { "仅支持有效的 http/https 网页链接" }
        return uri.toASCIIString()
    }
    fun diaryDate(value: String): String = try { LocalDate.parse(value).toString() }
        catch (_: Exception) { throw IllegalArgumentException("请选择有效的日记日期") }
    fun content(title: String, body: String, usableAttachments: Int) {
        require(title.length <= 120) { "标题最多 120 个字符" }
        require(body.length <= MAX_BODY) { "正文最多 50,000 个字符；请拆成多条记录，草稿仍会保留" }
        require(body.isNotBlank() || usableAttachments > 0) { "请填写正文或添加一份可用附件；只有标题不能保存" }
    }
}
