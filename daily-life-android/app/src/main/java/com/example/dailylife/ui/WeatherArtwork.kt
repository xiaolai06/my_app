package com.example.dailylife.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*

/** Original vector artwork: scales cleanly without downloads, text symbols or image assets. */
@Composable internal fun WeatherArtwork(code:Int?,isDay:Boolean,modifier:Modifier=Modifier) {
    val dark=LocalGlass.current.dark
    Canvas(modifier) {
        val scale=min(size.width,size.height)/128f
        scale(scale,scale,pivot=Offset.Zero) {
            val sun=code in setOf(0,1,2)
            val rain=code in setOf(51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99)
            val snow=code in setOf(71,73,75,77,85,86)
            val fog=code in setOf(45,48)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFA5B7EE).copy(alpha=.22f),Color.Transparent),Offset(64f,64f),63f),63f,Offset(64f,64f))
            if(sun) {
                val center=if(code==0) Offset(64f,58f) else Offset(83f,38f)
                if(isDay) {
                    repeat(8) {i->val a=i*PI/4;val start=Offset(center.x+cos(a).toFloat()*31,center.y+sin(a).toFloat()*31);val end=Offset(center.x+cos(a).toFloat()*37,center.y+sin(a).toFloat()*37)
                        drawLine(Color(0xFFE6AE57),start,end,3f,StrokeCap.Round)}
                    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFDB8A),Color(0xFFF2B65E))),24f,center)
                    drawCircle(Color.White.copy(alpha=.5f),24f,center,style=Stroke(1.4f))
                } else {
                    val moon=Path().apply {moveTo(center.x+15,center.y-22);cubicTo(center.x-25,center.y-28,center.x-35,center.y+25,center.x+5,center.y+26);cubicTo(center.x+20,center.y+28,center.x+28,center.y+15,center.x+28,center.y+8);cubicTo(center.x+4,center.y+12,center.x-1,center.y-6,center.x+15,center.y-22);close()}
                    drawPath(moon,Brush.linearGradient(listOf(Color(0xFFE0E6FF),Color(0xFF9FB1E9))))
                }
            }
            if(code!=0) {
                val cloud=Path().apply {moveTo(28f,84f);cubicTo(5f,83f,8f,52f,30f,51f);cubicTo(34f,22f,78f,22f,86f,50f);cubicTo(115f,44f,128f,84f,98f,86f);close()}
                translate(top=4f) {drawPath(cloud,Color(0xFF6076B3).copy(alpha=.1f))}
                drawPath(cloud,Brush.linearGradient(if(dark) listOf(Color(0xFFA8B9DB),Color(0xFF6D86B2)) else listOf(Color.White,Color(0xFFD8E3F5))))
                drawPath(cloud,Color.White.copy(alpha=.65f),style=Stroke(1.5f))
            }
            if(rain) repeat(3) {i->val x=40f+i*23f;drawLine(Color(0xFF6B9FD9),Offset(x,95f),Offset(x-5,106f),4f,StrokeCap.Round)}
            if(snow) repeat(3) {i->val x=37f+i*26;repeat(3) {j->val a=j*PI/3;val dx=cos(a).toFloat()*5;val dy=sin(a).toFloat()*5;drawLine(Color(0xFF759ED3),Offset(x-dx,101-dy),Offset(x+dx,101+dy),2f,StrokeCap.Round)}}
            if(fog) repeat(2) {i->drawLine(Color(0xFF8799BC),Offset(28f+i*8,96f+i*10),Offset(102f-i*8,96f+i*10),3f,StrokeCap.Round)}
            if(code in setOf(95,96,99)) drawPath(Path().apply {moveTo(72f,73f);lineTo(59f,96f);lineTo(72f,93f);lineTo(65f,114f);lineTo(87f,85f);lineTo(74f,87f);close()},Color(0xFFF1BD63))
        }
    }
}
