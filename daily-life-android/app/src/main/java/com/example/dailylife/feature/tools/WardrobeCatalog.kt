package com.example.dailylife.feature.tools

import com.example.dailylife.data.local.ToolEntry

val wardrobeTagSuggestions=listOf("通勤","休闲","简约","运动","旅行","约会","复古","正式")
fun wardrobeTags(raw:String)=raw.split(',', '，', '\n').map {it.trim()}.filter {it.isNotEmpty()}.distinct()
fun ToolEntry.styleTags()=wardrobeTags(field("styleTags"))

data class WardrobeFilter(val query:String="",val collection:String="all",val category:String="all",
    val occasion:String="",val tag:String="",val laundry:String="all",val membership:String="all",val sort:String="recent") {
    val hasDetails get()=category!="all" || occasion.isNotBlank() || tag.isNotBlank() || laundry!="all" || membership!="all" || sort!="recent"
}

/** Membership is derived from active outfits, never copied into a clothing record. */
class WardrobeCatalog(data:ToolsSnapshot) {
    val clothes=data.entries.filter {it.isClothing() && it.deletedAt==null && it.archivedAt==null}
    val outfits=data.entries.filter {it.kind=="outfit" && it.deletedAt==null && it.archivedAt==null}
    private val outfitById=outfits.associateBy {it.id}
    val memberships=data.links.filter {it.section=="outfit" && it.entityType=="tool" && it.entryId in outfitById}
        .groupBy {it.entityId}.mapValues {(_,links)->links.mapNotNull {outfitById[it.entryId]}.distinctBy {it.id}}
    val itemCounts=data.links.filter {it.section=="outfit" && it.entryId in outfitById}.groupingBy {it.entryId}.eachCount()
    val wornCounts=data.logs.filter {it.kind=="wear"}.groupingBy {it.entryId}.eachCount()
    fun entries(outfit:Boolean)=if(outfit) outfits else clothes
    fun tags(outfit:Boolean)=entries(outfit).flatMap {it.styleTags()}.distinct().sorted()
    fun occasions(outfit:Boolean)=entries(outfit).map {it.field("occasion").trim()}.filter {it.isNotBlank()}.distinct().sorted()
    fun filter(outfit:Boolean,f:WardrobeFilter):List<ToolEntry> {
        val result=entries(outfit).filter {e->
            (f.collection!="pinned" || e.pinned) && (f.collection!="favorites" || e.isFavorite()) &&
                (outfit || f.category=="all" || e.field("clothingType","top")==f.category) &&
                (f.occasion.isBlank() || e.field("occasion").trim()==f.occasion) && (f.tag.isBlank() || f.tag in e.styleTags()) &&
                (outfit || f.laundry=="all" || e.field("laundry","ready")==f.laundry) &&
                (outfit || f.membership=="all" || memberships[e.id].orEmpty().isEmpty()==(f.membership=="single")) &&
                (f.query.isBlank() || (listOf(e.title,e.note,clothingHint(e))+e.styleTags()+memberships[e.id].orEmpty().map {it.title}).joinToString(" ").contains(f.query.trim(),true))
        }
        val order=when(f.sort) {"name"->compareBy<ToolEntry> {it.title};"oldest"->compareBy {it.createdAt};else->compareByDescending {it.updatedAt}}
        return result.sortedWith(compareByDescending<ToolEntry> {it.pinned}.then(order).thenBy {it.id})
    }
}
