package com.example.dailylife.data.repository

import com.example.dailylife.data.local.DailyLifeDatabase
import com.example.dailylife.data.local.ToolLink
import java.util.UUID

/** Called inside the original object's transaction; a failed binding leaves its draft intact. */
suspend fun bindToProject(db:DailyLifeDatabase,projectId:String?,type:String,id:String,owner:String) {
    if(projectId.isNullOrBlank()) return
    require(runCatching {UUID.fromString(projectId);UUID.fromString(id)}.isSuccess) {"专题或内容编号无效，请保留输入后重新选择"}
    val source=db.tools().entries().find {it.id==projectId}
    require(source!=null && source.kind=="project" && source.ownerId==owner && source.deletedAt==null && source.archivedAt==null) {"原专题已归档、删除或不可用，请保留草稿后重新选择专题"}
    val targetType=when(type) {"record","task","event","habit","focus","goal"->type;"transaction","ledger"->"ledger";"checklist","checklist_template","anniversary","subscription","bill","item","loan","project","tool"->"tool";else->error("此内容暂不支持加入专题")}
    require(targetType!="tool" || id!=projectId) {"专题不能关联自身"}
    val section=when(targetType) {"record"->"record";"ledger"->"ledger";"tool"->if(db.tools().entries().find {it.id==id}?.kind=="checklist") "checklist" else "material";"goal"->"goal";else->"task"}
    val links=db.tools().links().filter {it.entryId==projectId}
    if(links.none {it.entityType==targetType && it.entityId==id}) db.tools().put(ToolLink(projectId,targetType,id,section,(links.maxOfOrNull {it.sortOrder} ?: -1)+1))
}
