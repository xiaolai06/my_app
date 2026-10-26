package com.example.dailylife.core
import java.math.BigDecimal
import java.math.RoundingMode
object LedgerRules {
    const val MAX_MINOR=99_999_999_999L
    fun minor(value:String,allowZero:Boolean=false,signed:Boolean=false):Long {
        val clean=value.trim();require(clean.matches(Regex(if(signed) "[-+]?\\d+(\\.\\d{1,2})?" else "\\d+(\\.\\d{1,2})?"))) {"金额最多保留两位小数"}
        val n=BigDecimal(clean).movePointRight(2).longValueExact()
        require(n in -MAX_MINOR..MAX_MINOR && (signed || if(allowZero) n>=0 else n>0)) {"金额超出范围或需大于零"};return n
    }
    fun display(value:Long)=BigDecimal.valueOf(value,2).setScale(2,RoundingMode.UNNECESSARY).toPlainString()
    fun name(value:String):String {val s=value.trim();require(s.isNotEmpty() && s.length<=120) {"名称需为 1 至 120 字"};return s}
    fun csv(text:String):List<List<String>> {
        require(text.length<=10_000_000) {"CSV 不能超过 10 MB"};val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val field=StringBuilder();var quote=false;var closed=false;var i=if(text.startsWith('\uFEFF')) 1 else 0
        fun endField(){row+=field.toString();field.clear();closed=false}
        fun endRow(){endField();if(row.any {it.isNotEmpty()}) rows+=row;row=mutableListOf()}
        while(i<text.length) {val c=text[i]
            if(quote) {if(c=='"') {if(i+1<text.length && text[i+1]=='"') {field.append('"');i++} else {quote=false;closed=true}} else field.append(c)}
            else when(c) {'"'->{require(field.isEmpty()&&!closed) {"CSV 引号格式错误"};quote=true};','->endField();'\r','\n'->{endRow();if(c=='\r'&&i+1<text.length&&text[i+1]=='\n') i++};else->{require(!closed||c==' '||c=='\t') {"CSV 引号后有多余内容"};if(!closed) field.append(c)}}
            i++;require(rows.size<=5000 && row.size<=100 && field.length<=50000) {"CSV 最多 5000 行、100 列，每字段 50000 字"}
        };require(!quote) {"CSV 引号未闭合"};if(field.isNotEmpty()||row.isNotEmpty()||closed) endRow();return rows
    }
    fun csvCell(value:String)="\""+value.replace("\"","\"\"")+"\""
}
