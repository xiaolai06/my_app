package com.example.dailylife.feature.tools

import com.example.dailylife.data.local.ToolEntry
import java.time.LocalDate

val clothingCategories=linkedMapOf("top" to "上装","bottom" to "下装","dress" to "连衣裙","coat" to "外套","shoes" to "鞋履","bag" to "包袋","accessory" to "配饰")
val clothingWarmth=linkedMapOf("all" to "不限温度","light" to "轻薄","layer" to "适合叠穿","warm" to "保暖")
fun ToolEntry.isClothing()=kind=="item" && field("wardrobe")=="true"
fun ToolEntry.isFavorite()=field("favorite")=="true"
fun clothingHint(e:ToolEntry)=listOfNotNull(clothingCategories[e.field("clothingType")],e.field("color").ifBlank {null},e.field("occasion").ifBlank {null}).joinToString(" · ")

data class LifeSuggestion(val id:String,val title:String,val reason:String,val kind:String="tool")
/** Explicit local rules, bounded results, no profiles uploaded or invented recommendations. */
fun ToolsSnapshot.suggestions(today:LocalDate,temperature:Double?=null,rainChance:Int?=null):List<LifeSuggestion> {
    val available=entries.filter {it.deletedAt==null && it.archivedAt==null && it.state in setOf("active","idle","trial")}
    val urgent=available.filter {it.kind=="item"}.mapNotNull {e->
        listOf("expiry" to "到期", "careDate" to "养护", "warranty" to "保修到期").mapNotNull {(field,label)->
            runCatching {LocalDate.parse(e.field(field))}.getOrNull()?.takeIf {it in today..today.plusDays(7)}?.let {date->
                LifeSuggestion(e.id,e.title,"$date · $label")
            }
        }.firstOrNull()
    }
    val clothing=available.filter {it.isClothing() && it.state=="active" && it.field("laundry","ready")=="ready"}
        .filter {e->temperature!=null && when(e.field("warmth","all")) {"light"->temperature>=24;"layer"->temperature in 15.0..24.0;"warm"->temperature<15;else->false}}
        .sortedWith(compareByDescending<ToolEntry> {it.isFavorite()}.thenBy {it.updatedAt})
        .take(2).map {LifeSuggestion(it.id,it.title,"当前约 ${temperature?.toInt()}° · "+clothingWarmth[it.field("warmth")]+if((rainChance ?: 0)>=60) "，今日可能有雨，出门前再确认" else "")}
    val favorites=available.filter {it.isFavorite() && it.kind in setOf("item","outfit")}.take(3)
        .map {LifeSuggestion(it.id,it.title,"你的收藏 · 随时查看")}
    return (urgent+clothing+favorites).distinctBy {it.id}.take(5)
}
