package com.zeus.v2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*

fun calculateBandResponse(freq: Float, band: EqBand): Float {
    val f0=band.frequency.coerceAtLeast(1f); val w=freq/f0; val gainDb=band.gain
    return when(band.filterType){
        EqBand.FilterType.PEAK->{val bw=1f/band.q.coerceAtLeast(.1f);val x=(ln(w)).pow(2)/(2f*bw*bw);gainDb*exp(-x)}
        EqBand.FilterType.LOW_SHELF->if(freq<=f0)gainDb else gainDb*.15f
        EqBand.FilterType.HIGH_SHELF->if(freq>=f0)gainDb else gainDb*.15f
        EqBand.FilterType.LOW_PASS->{val order=(band.q*2f).coerceIn(1f,6f);-12f*log10(1f+(freq/f0).pow(order))}
        EqBand.FilterType.HIGH_PASS->{val order=(band.q*2f).coerceIn(1f,6f);-12f*log10(1f+(f0/freq).pow(order))}
        EqBand.FilterType.NOTCH->{-30f*exp(-((ln(w)).pow(2))*band.q)}
        EqBand.FilterType.BAND_PASS->{gainDb*exp(-((ln(w)).pow(2))*band.q*.5f)}
        EqBand.FilterType.BYPASS->0f
    }.coerceIn(-30f,30f)
}

@Composable
fun EqGraph(bands:List<EqBand>,selectedIndex:Int,spectrum:FloatArray,onBandSelected:(Int)->Unit,onBandMoved:(Int,Float,Float)->Unit,modifier:Modifier=Modifier){
    val minFreq=18f;val maxFreq=20000f;val minGain=-30f;val maxGain=30f
    fun freqToX(freq:Float,width:Float):Float{val a=ln(minFreq);val b=ln(maxFreq);return ((ln(freq.coerceIn(minFreq,maxFreq))-a)/(b-a))*width}
    fun xToFreq(x:Float,width:Float):Float{val a=ln(minFreq);val b=ln(maxFreq);return exp(a+(x/width).coerceIn(0f,1f)*(b-a))}
    val responsePoints=remember(bands){FloatArray(320){i->val t=i.toFloat()/319f;val f=exp(ln(minFreq)+t*(ln(maxFreq)-ln(minFreq)));var total=0f;for(bb in bands)if(bb.enabled)total+=calculateBandResponse(f,bb);total.coerceIn(minGain,maxGain)}}
    var displaySpectrum by remember{mutableStateOf(FloatArray(0))}
    LaunchedEffect(spectrum){
        if(spectrum.isEmpty()){displaySpectrum=FloatArray(0);return@LaunchedEffect}
        if(displaySpectrum.size!=spectrum.size){displaySpectrum=spectrum.copyOf();return@LaunchedEffect}
        repeat(8){val next=FloatArray(spectrum.size){i->val target=spectrum[i].coerceIn(0f,1f);val cur=displaySpectrum[i];cur+(target-cur)*.38f};displaySpectrum=next;delay(12)}
        displaySpectrum=spectrum.map{it.coerceIn(0f,1f)}.toFloatArray()
    }
    val infinite=rememberInfiniteTransition(label="eqGraphTick")
    val tick by infinite.animateFloat(0f,1f,infiniteRepeatable(tween(1000,easing=LinearEasing),RepeatMode.Restart),label="tick")
    Box(modifier.background(Color(0xFF12141A)).padding(6.dp)){
        Canvas(Modifier.fillMaxSize().pointerInput(bands){detectTapGestures{off->var closest=-1;var best=Float.MAX_VALUE;bands.forEachIndexed{idx,b->val bx=freqToX(b.frequency,size.width.toFloat());val d=abs(bx-off.x);if(d<best){best=d;closest=idx}};if(closest>=0&&best<90f)onBandSelected(closest)}}.pointerInput(selectedIndex,bands){detectDragGestures{change,_->change.consume();val idx=selectedIndex;if(idx !in bands.indices)return@detectDragGestures;val w=size.width.toFloat();val h=size.height.toFloat();val f=xToFreq(change.position.x,w).coerceIn(18f,20000f);val g=(maxGain-(change.position.y/h)*(maxGain-minGain)).coerceIn(minGain,maxGain);onBandMoved(idx,f,g)}}){
            if(tick.isNaN())return@Canvas;val w=size.width;val h=size.height;val midY=h/2f
            for(i in 0..6){val y=h*i/6f;drawLine(Color(0xFF242832),Offset(0f,y),Offset(w,y),1f)}
            val gridFreqs=floatArrayOf(18f,31.5f,63f,125f,250f,500f,1000f,2000f,4000f,8000f,16000f,20000f)
            gridFreqs.forEach{f->val x=freqToX(f,w);drawLine(Color(0xFF242832),Offset(x,0f),Offset(x,h),1f)}
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(120,124,136);textSize=22f}
            gridFreqs.forEach{f->val x=freqToX(f,w);val label=if(f>=1000f)"${(f/1000f).toInt()}k" else "${f.toInt()}";drawContext.canvas.nativeCanvas.drawText(label,(x+2f).coerceAtMost(w-42f),h-8f,paint)}
            for(db in intArrayOf(30,20,10,0,-10,-20,-30)){val y=(1f-((db-minGain)/(maxGain-minGain)))*h;drawContext.canvas.nativeCanvas.drawText("${db}dB",4f,(y-4f).coerceAtLeast(18f),paint)}
            drawLine(Color(0xFF596273),Offset(0f,midY),Offset(w,midY),1.5f)
            if(displaySpectrum.isNotEmpty()){
                val n=(displaySpectrum.size-1).coerceAtLeast(1);val spPath=Path();val spFill=Path();val nyquist=24000f;val logMin=ln(minFreq);val logMax=ln(maxFreq)
                for(i in displaySpectrum.indices){val t=i.toFloat()/n;val targetFreq=exp(logMin+t*(logMax-logMin));val linearIndex=(targetFreq/nyquist*n).coerceIn(0f,n.toFloat());val lo=floor(linearIndex).toInt().coerceIn(0,n);val hi=ceil(linearIndex).toInt().coerceIn(0,n);val frac=linearIndex-lo;val v=displaySpectrum[lo]*(1f-frac)+displaySpectrum[hi]*frac;val x=w*t;val normalized=((v-.01f)/.99f).coerceIn(0f,1f);val amp=(normalized.pow(.45f)*.86f).coerceIn(0f,.92f);val y=h*(1f-amp);if(i==0){spPath.moveTo(x,y);spFill.moveTo(x,h);spFill.lineTo(x,y)}else{spPath.lineTo(x,y);spFill.lineTo(x,y)}}
                spFill.lineTo(w,h);spFill.close();drawPath(spFill,brush=Brush.verticalGradient(listOf(Color(0x554060A0),Color(0x00000000))));drawPath(spPath,color=Color(0xAA7C4DFF),style=Stroke(width=1.8f,cap=StrokeCap.Round))
            }
            if(responsePoints.isNotEmpty()){val n=responsePoints.size-1;val linePath=Path();val fillPath=Path();for(i in responsePoints.indices){val x=w*i/n.toFloat();val y=(1f-((responsePoints[i]-minGain)/(maxGain-minGain)))*h;if(i==0){linePath.moveTo(x,y);fillPath.moveTo(x,midY);fillPath.lineTo(x,y)}else{linePath.lineTo(x,y);fillPath.lineTo(x,y)}};fillPath.lineTo(w,midY);fillPath.close();drawPath(fillPath,brush=Brush.verticalGradient(listOf(Color(0x663B82F6),Color(0x223B82F6),Color(0x00000000))));drawPath(linePath,color=Color.White,style=Stroke(width=2.6f,cap=StrokeCap.Round))}
            bands.forEachIndexed{idx,b->if(!b.enabled)return@forEachIndexed;val x=freqToX(b.frequency,w);val y=(1f-((b.gain-minGain)/(maxGain-minGain)))*h;val selected=idx==selectedIndex;val r=if(selected)9f else 6f;if(selected)drawCircle(b.color.copy(alpha=.28f),r+7f,Offset(x,y));drawCircle(b.color,r,Offset(x,y));if(selected)drawCircle(Color.White,2.5f,Offset(x,y))}
        }
    }
}
