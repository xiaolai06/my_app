package com.example.dailylife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.ToolEntry
import com.example.dailylife.feature.ledger.LedgerSnapshot
import com.example.dailylife.feature.tools.*

@Composable internal fun ToolItemTransactions(item:ToolEntry,ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,open:(String,String)->Unit) {
    var choosing by remember(item.id) {mutableStateOf<String?>(null)}
    var removing by remember(item.id) {mutableStateOf<Pair<String,String>?>(null)}
    SectionLabel("购买与出售账目","物品金额、物品日期与原账目分别保存；引用不会自动修改物品事实。")
    listOf("item_purchase" to "购买支出","item_sale" to "出售收入").forEach {(role,label)->
        val link=ui.data.links.find {it.entryId==item.id && it.section==role}
        val transaction=ledger.transactions.find {it.id==link?.entityId}
        GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(label,style=MaterialTheme.typography.titleMedium)
            if(link==null) Text("尚未引用原账目",color=LocalGlass.current.muted)
            else if(transaction==null || transaction.deletedAt!=null) Text("原账目已删除或不存在，物品事实保留",color=LocalGlass.current.muted)
            else {
                Text(transaction.subject.ifBlank {label}+" · ¥"+toolMoney(transaction.amountMinor,ui.hideAmounts))
                Text("账目日期："+transaction.occurredOn,color=LocalGlass.current.muted)
                TextButton(onClick={open("ledger",transaction.id)}) {Text("查看原账目")}
            }
            if(item.deletedAt==null) Row {
                TextButton(enabled=!ui.busy,onClick={choosing=role}) {Text(if(link==null) "选择"+label else "更换引用")}
                if(link!=null) TextButton(enabled=!ui.busy,onClick={removing=role to link.entityId}) {Text("解除引用")}
            }
        }}
    }
    choosing?.let {role->ToolItemTransactionSheet(item,role,ui,vm,ledger) {choosing=null}}
    removing?.let {(_,id)->ConfirmDialog("解除物品账目引用？","原账目以及物品的金额和日期均保留。",{removing=null},{vm.run("引用已解除，原账目保留",onSuccess={removing=null}) {unlink(item.id,"ledger",id)}})}
}

@Composable private fun ToolItemTransactionSheet(item:ToolEntry,role:String,ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,dismiss:()->Unit) {
    var query by remember {mutableStateOf("")};var selected by remember {mutableStateOf("")};var importFacts by remember {mutableStateOf(false)}
    val purchase=role=="item_purchase";val transactions=ledger.transactions.filter {it.deletedAt==null && it.type==if(purchase) "expense" else "income"}.sortedByDescending {it.occurredOn}
    GlassSheet(if(purchase) "引用购买支出" else "引用出售收入",dismiss) {
        ToolField("搜索商户、用途或备注",query,{query=it})
        transactions.filter {(it.subject+it.counterparty+it.note).contains(query.trim(),true)}.forEach {transaction->
            GlassChoice(transaction.subject.ifBlank {if(purchase) "购买支出" else "出售收入"}+" · "+transaction.occurredOn+" · ¥"+toolMoney(transaction.amountMinor,ui.hideAmounts),selected==transaction.id,Modifier.fillMaxWidth()) {selected=transaction.id}
        }
        if(transactions.isEmpty()) Text("暂无可引用的"+if(purchase) "购买支出，请先在账本记账。" else "出售收入，请先在账本记账。",color=LocalGlass.current.muted)
        transactions.find {it.id==selected}?.let {transaction->
            HorizontalDivider();Text("确认前预览",style=MaterialTheme.typography.titleMedium)
            Text("原物品："+item.field(if(purchase) "date" else "soldDate").ifBlank {"日期未填"}+" · "+(if(ui.hideAmounts) "••••" else item.field(if(purchase) "amount" else "soldAmount").ifBlank {"金额未填"}))
            Text("原账目："+transaction.occurredOn+" · ¥"+toolMoney(transaction.amountMinor,ui.hideAmounts))
            ToolSwitch("同时用原账目金额与日期填写物品",importFacts) {importFacts=it}
            Text(if(importFacts) "确认后替换物品的这两项字段；原账目不变，物品使用状态不变。" else "仅建立引用，保留物品当前填写的金额与日期。",color=LocalGlass.current.muted)
        }
        GlassButton(enabled=!ui.busy && selected.isNotBlank(),onClick={vm.run(if(importFacts) "已确认带入并引用" else "已建立引用，物品事实保留",onSuccess=dismiss) {itemTransaction(item.id,role,selected,importFacts)}},modifier=Modifier.fillMaxWidth()) {Text(if(importFacts) "确认带入并引用" else "确认引用")}
    }
}
