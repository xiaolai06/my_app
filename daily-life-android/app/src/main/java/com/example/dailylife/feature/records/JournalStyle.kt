package com.example.dailylife.feature.records

import org.json.JSONObject

/** Presentation is separate from the user's writing and attachment originals. */
data class JournalStyle(
    val paper:String="glass", val accent:String="iris", val layout:String="story",
    val sticker:String="", val caption:String=""
) {
    fun json()=JSONObject().put("version",1).put("paper",paper).put("accent",accent)
        .put("layout",layout).put("sticker",sticker).put("caption",caption).toString()
    fun validate() {
        require(paper in papers.keys && accent in accents.keys && layout in layouts.keys) {"手账样式不支持，请重新选择"}
        require(sticker in stickers && caption.length<=120) {"手账题签最多 120 字"}
    }
    companion object {
        val papers=linkedMapOf("glass" to "玻璃", "cream" to "奶油纸", "ruled" to "横线", "dots" to "点阵")
        val accents=linkedMapOf("iris" to "鸢尾紫", "rose" to "玫瑰", "mint" to "薄荷", "amber" to "暖杏")
        val layouts=linkedMapOf("story" to "图文", "collage" to "拼贴", "film" to "拍立得")
        val stickers=listOf("", "✦", "♡", "☀", "☁", "❀", "♫")
        fun parse(raw:String):JournalStyle {
            val j=JSONObject(raw)
            require(j.optInt("version",1)==1) {"此手账样式版本暂不支持"}
            return JournalStyle(j.optString("paper","glass"),j.optString("accent","iris"),
                j.optString("layout","story"),j.optString("sticker"),j.optString("caption")).also {it.validate()}
        }
    }
}
