package com.example.dailylife.core
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
object ToolRules {
    fun milli(s:String)=s.trim().toBigDecimal().movePointRight(3).longValueExact().also {require(it in 1..1_000_000_000_000L) {"数量需大于 0，最多三位小数"}}
    fun minor(s:String)=s.trim().toBigDecimal().movePointRight(2).longValueExact().also {require(it in 0..99_999_999_999L) {"金额需非负，最多两位小数"}}
    fun number(v:Long)=BigDecimal.valueOf(v,3).stripTrailingZeros().toPlainString()
    fun money(v:Long)=BigDecimal.valueOf(v,2).setScale(2).toPlainString()
    fun date(s:String)=LocalDate.parse(s).toString()
    fun aa(amount:String,people:String):List<Long> {val total=minor(amount);val n=people.toInt();require(n in 1..1000) {"人数需为 1 至 1000"};return List(n){total/n+if(it<total%n) 1 else 0}}
    /** Explicit percent means x / 100; no locale-dependent calculator semantics. */
    fun calculate(source:String):BigDecimal {
        require(source.length in 1..500) {"请输入 1 至 500 字的算式"}
        val input=source.replace("×","*").replace("÷","/").filterNot {it.isWhitespace()};var pos=0;var depth=0
        lateinit var expression:()->BigDecimal
        lateinit var term:()->BigDecimal
        lateinit var factor:()->BigDecimal
        factor={
            require(++depth<=40) {"括号层数过多"}
            val negative=pos<input.length && input[pos]=='-';if(pos<input.length && input[pos] in "+-") pos++
            var value=if(pos<input.length && input[pos]=='(') {pos++;val n=expression();require(pos<input.length && input[pos]==')') {"括号不匹配"};pos++;n}
                else {val from=pos;while(pos<input.length && (input[pos].isDigit() || input[pos]=='.')) pos++;require(pos>from) {"请输入有效数字"};input.substring(from,pos).toBigDecimal()}
            if(negative) value=value.negate()
            if(pos<input.length && input[pos]=='%') {pos++;value=value.movePointLeft(2)}
            depth--;value
        }
        term={var n=factor();while(pos<input.length && input[pos] in "*/") {val op=input[pos++];val r=factor();n=if(op=='*') n.multiply(r) else {require(r.signum()!=0) {"不能除以 0"};n.divide(r,12,RoundingMode.HALF_UP)}};n}
        expression={var n=term();while(pos<input.length && input[pos] in "+-") {val op=input[pos++];val r=term();n=if(op=='+') n.add(r) else n.subtract(r)};n}
        val result=expression();require(pos==input.length) {"算式包含无法识别的内容"};require(result.precision()<=100) {"计算结果过大"};return result.stripTrailingZeros()
    }
}
