package com.example.dailylife.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.dailylife.feature.plans.PlansSnapshot
import java.time.*
@Composable internal fun PlanMonth(selected:LocalDate,select:(String)->Unit,data:PlansSnapshot,extraMarked:Set<String> = emptySet()) {
    val month=YearMonth.from(selected)
    val marked=data.taskRows().flatMap {listOfNotNull(it.date,it.due)}.toSet()+
        data.events.filter {it.deletedAt==null && it.archivedAt==null}.map {it.startDate}+
        data.occurrences.filter {it.entityType=="event" && it.status!="cancelled" && data.events.any {e->e.id==it.entityId && e.deletedAt==null && e.archivedAt==null}}.map {it.scheduledDate}+extraMarked
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<336.dp || LocalDensity.current.fontScale>1.3f) {
            Column {
                Text("本周 · 点日期查看安排",style=MaterialTheme.typography.labelMedium)
                val start=selected.with(DayOfWeek.MONDAY)
                for(n in 0L..6L) {
                    val d=start.plusDays(n)
                    GlassChoice(d.toString()+(if(d.toString() in marked) " · 有安排" else ""),d==selected,Modifier.fillMaxWidth()) {select(d.toString())}
                }
            }
        } else Column {
            Row {listOf("一","二","三","四","五","六","日").forEach {Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
            val first=month.atDay(1).dayOfWeek.value-1
            val cells=((first+month.lengthOfMonth()+6)/7)*7
            for(row in 0 until cells/7) Row {
                for(col in 0..6) {
                    val number=row*7+col-first+1
                    Box(Modifier.weight(1f)) {
                        if(number in 1..month.lengthOfMonth()) {val d=month.atDay(number)
                            TextButton(onClick={select(d.toString())},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                                contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.textButtonColors(
                                    containerColor=if(d==selected) LocalGlass.current.selected else androidx.compose.ui.graphics.Color.Transparent)) {
                                Text(number.toString()+(if(d.toString() in marked) "\n·" else ""))
                            }
                        }
                    }
                }
            }
        }
    }
}
