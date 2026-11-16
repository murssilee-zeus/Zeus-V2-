package com.zeus.v2

import androidx.compose.ui.graphics.Color

data class EqBand(val id:Int,var frequency:Float=1000f,var gain:Float=0f,var q:Float=1f,var enabled:Boolean=true,var filterType:FilterType=FilterType.PEAK,val color:Color){
    enum class FilterType{LOW_SHELF,HIGH_SHELF,PEAK,LOW_PASS,HIGH_PASS,NOTCH,BAND_PASS,BYPASS}
    fun copyValuesFrom(other:EqBand){frequency=other.frequency.coerceIn(1f,30000f);gain=other.gain.coerceIn(-30f,30f);q=other.q.coerceIn(.1f,40f);enabled=other.enabled;filterType=other.filterType}
}
private val BAND_COLORS=listOf(Color(0xFFE0566B),Color(0xFFFF9F43),Color(0xFFFFEAA7),Color(0xFF55EFC4),Color(0xFF74B9FF),Color(0xFFA29BFE),Color(0xFFFF6B9E),Color(0xFF00CEC9),Color(0xFFE17055),Color(0xFF6C5CE7),Color(0xFFFF7675),Color(0xFFFDCB6E),Color(0xFF00B894),Color(0xFF0984E3),Color(0xFFC160FF),Color(0xFFE84393),Color(0xFF2D3436),Color(0xFFD63031))
fun createDefaultBands():List<EqBand>{val d=listOf(Triple(20f,5f,.7f),Triple(31.5f,4f,.85f),Triple(50f,3f,1f),Triple(80f,2f,1f),Triple(125f,1.5f,1.1f),Triple(200f,.8f,1.1f),Triple(315f,.3f,1f),Triple(500f,0f,1f),Triple(800f,0f,1f),Triple(1250f,0f,1f),Triple(2000f,0f,1.1f),Triple(3150f,0f,1.2f),Triple(5000f,0f,1.2f),Triple(8000f,0f,1f),Triple(12500f,-.3f,.9f),Triple(20000f,-1f,.8f));return d.mapIndexed{i,(f,g,q)->EqBand(i,f,g,q,true,when(i){0->EqBand.FilterType.LOW_SHELF;15->EqBand.FilterType.HIGH_SHELF;else->EqBand.FilterType.PEAK},BAND_COLORS[i%BAND_COLORS.size])}}
fun createNewBand(id:Int,frequency:Float=1000f)=EqBand(id,frequency.coerceIn(1f,30000f),0f,1f,true,EqBand.FilterType.PEAK,BAND_COLORS[id%BAND_COLORS.size])
// final Cross 4 verification trigger
