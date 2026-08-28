package com.zeus.v2

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ZBG=Color(0xFF08090C); private val ZSUR=Color(0xFF12131A); private val ZBR=Color(0xFF2A2C36)
private val ZP=Color(0xFFB65CFF); private val ZPK=Color(0xFFFF5AA5); private val ZG=Color(0xFF25D17F)
private val ZT=Color(0xFFF1F1F4); private val ZM=Color(0xFF8D8F9A)

@Composable
fun ZeusStudioScreenV2(vm:EqViewModel,punch:PunchViewModel,onToggleEngine:()->Unit,onSave:()->Unit){
 var page by remember{mutableIntStateOf(0)}
 Column(Modifier.fillMaxSize().background(ZBG).padding(10.dp)){
  Row(Modifier.fillMaxWidth().padding(bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Row(verticalAlignment=Alignment.CenterVertically){Text(if(page==0)"Equalizer" else "Dynamics",color=ZT,fontSize=19.sp,fontWeight=FontWeight.Bold);Text("›",color=ZP,fontSize=28.sp,modifier=Modifier.clickable{page=(page+1)%2}.padding(start=4.dp))}
   Row(verticalAlignment=Alignment.CenterVertically){Text("Guardar",color=Color.White,fontSize=12.sp,modifier=Modifier.background(Color(0xFF0E4D3A),RoundedCornerShape(18.dp)).border(1.dp,ZG,RoundedCornerShape(18.dp)).clickable{onSave()}.padding(horizontal=14.dp,vertical=8.dp));Spacer(Modifier.width(10.dp));Text(if(vm.isEngineRunning)"●" else "○",color=if(vm.isEngineRunning) ZG else ZM,fontSize=26.sp,modifier=Modifier.clickable{onToggleEngine()})}
  }
  if(page==0) EqPage(vm,punch) else DynPage(vm)
 }
}

@Composable private fun EqPage(vm:EqViewModel,punch:PunchViewModel){
 Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Column(Modifier.weight(1.35f),verticalArrangement=Arrangement.spacedBy(6.dp)){
   EqGraph(vm.bands,vm.selectedBandIndex,vm.spectrum,{vm.selectBand(it)},{i,f,g->vm.selectBand(i);vm.updateSelectedBand(frequency=f,gain=g)},Modifier.fillMaxWidth().weight(1f))
   Filters(vm); BandEdit(vm); Presets(vm); Bands(vm)
  }
  Column(Modifier.weight(.9f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
   PunchCard(punch,vm); Pipe(vm)
  }
 }
}

@Composable private fun DynPage(vm:EqViewModel){Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){CompCard(vm)};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){LimCard(vm)}}}

@Composable private fun Card(title:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().background(ZSUR,RoundedCornerShape(10.dp)).border(1.dp,ZBR,RoundedCornerShape(10.dp)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,color=ZP,fontSize=13.sp,fontWeight=FontWeight.Bold);content()}}
@Composable private fun S(label:String,value:Float,range:ClosedFloatingPointRange<Float>,unit:String="",modifier:Modifier=Modifier.fillMaxWidth(),change:(Float)->Unit){Column(modifier){Row{Text(label,color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f));var show by remember{mutableStateOf(false)}; Text(fmt(value)+unit,color=ZT,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{show=true}.padding(4.dp)); if(show){NumberDialog(label,value,range.start,range.endInclusive,unit,{change(it);show=false},{show=false})}};Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=change,valueRange=range,colors=SliderDefaults.colors(thumbColor=ZP,activeTrackColor=ZP,inactiveTrackColor=ZBR))}}

@Composable private fun PunchCard(p:PunchViewModel,vm:EqViewModel){Card("PUNCH (35Hz - 65Hz)"){Text("Post-MBC · 18 Hz stays independent",color=ZM,fontSize=9.sp);S("Amount",p.amount,0f..100f," %"){p.updatePunchAmount(it)};S("Center",49f,35f..65f," Hz"){};S("Q",1.2f,.5f..3f){};S("Headroom trim",vm.headroomTrim,-12f..6f," dB"){vm.headroomTrim=it}\n  Text("Headroom protection · manual trim",color=ZM,fontSize=9.sp)}}

@Composable private fun CompCard(vm:EqViewModel){var b by remember{mutableIntStateOf(0)};val n=listOf("LOW","LO-MID","HI-MID","HIGH");val th=listOf(vm.compMbThLow,vm.compMbThLoMid,vm.compMbThHiMid,vm.compMbThHigh);val ra=listOf(vm.compMbRatioLow,vm.compMbRatioLoMid,vm.compMbRatioHiMid,vm.compMbRatioHigh);val at=listOf(vm.compMbAttackLow,vm.compMbAttackLoMid,vm.compMbAttackHiMid,vm.compMbAttackHigh);val re=listOf(vm.compMbReleaseLow,vm.compMbReleaseLoMid,vm.compMbReleaseHiMid,vm.compMbReleaseHigh)
 Card("COMPRESOR MULTIBANDA"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){n.forEachIndexed{i,x->Text(x,color=if(b==i)Color.Black else ZT,fontSize=8.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f).background(if(b==i)ZP else Color(0xFF1A1B22),RoundedCornerShape(5.dp)).clickable{b=i}.padding(vertical=6.dp))}};S("Threshold",th[b],-60f..0f," dB"){uTh(vm,b,it)};S("Ratio",ra[b],1f..20f," :1"){uRa(vm,b,it)};S("Attack",at[b],1f..100f," ms"){uAt(vm,b,it)};S("Release",re[b],20f..500f," ms"){uRe(vm,b,it)};Row{S("C1",vm.crossoverFrequencies[0],40f..1000f," Hz",Modifier.weight(1f)){vm.setCrossover(0,it)};S("C2",vm.crossoverFrequencies[1],100f..5000f," Hz",Modifier.weight(1f)){vm.setCrossover(1,it)};S("C3",vm.crossoverFrequencies[2],1000f..19500f," Hz",Modifier.weight(1f)){vm.setCrossover(2,it)}};Row(verticalAlignment=Alignment.CenterVertically){Text("Activado",color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f));Switch(vm.compressorMultibandEnabled,{vm.compressorMultibandEnabled=it})}}}

@Composable private fun LimCard(vm:EqViewModel){Card("LIMITADOR"){S("Threshold",vm.limiterThreshold,-12f..0f," dB"){vm.limiterThreshold=it};S("Attack",vm.limiterAttack,.1f..20f," ms"){vm.limiterAttack=it};S("Release",vm.limiterRelease,20f..500f," ms"){vm.limiterRelease=it};S("Ratio",vm.limiterRatio,1f..30f," :1"){vm.limiterRatio=it};S("Post Gain",vm.limiterPostGain,-12f..12f," dB"){vm.limiterPostGain=it};Row(verticalAlignment=Alignment.CenterVertically){Text("Activado",color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f));Switch(vm.limiterEnabled,{vm.limiterEnabled=it})}}}
@Composable private fun Pipe(vm:EqViewModel){Card("AUDIO EFFECTS PIPELINE"){Text("1  MBC · Procesamiento multibanda",color=ZT,fontSize=10.sp);Text("2  PUNCH 35-65 Hz · Controlado de subgraves",color=ZT,fontSize=10.sp);Text("3  LIMITER · Protección final de picos",color=ZT,fontSize=10.sp);Text("DIRECT OUTPUT · ACTIVO",color=ZG,fontSize=10.sp,fontWeight=FontWeight.Bold)}}

@Composable private fun Filters(vm:EqViewModel){val b=vm.selectedBand();val t=listOf(EqBand.FilterType.PEAK to "PEAK",EqBand.FilterType.LOW_SHELF to "LOW",EqBand.FilterType.HIGH_SHELF to "HIGH",EqBand.FilterType.LOW_PASS to "LPF",EqBand.FilterType.HIGH_PASS to "HPF",EqBand.FilterType.BYPASS to "BYPASS");Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){t.forEach{(x,l)->Text(l,color=if(b?.filterType==x)Color.Black else ZT,fontSize=8.sp,modifier=Modifier.background(if(b?.filterType==x) ZPK else ZSUR,RoundedCornerShape(6.dp)).border(1.dp,ZBR,RoundedCornerShape(6.dp)).clickable{vm.updateSelectedBand(filterType=x)}.padding(horizontal=8.dp,vertical=6.dp))}}}
@Composable private fun BandEdit(vm:EqViewModel){
 val b=vm.selectedBand()?:return
 Card("BANDA ${vm.selectedBandIndex+1}"){
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
   EditableBox("FREQ",b.frequency,"Hz",1f,30000f,Modifier.weight(1f)){vm.updateSelectedBand(frequency=it)}
   EditableBox("GAIN",b.gain,"dB",-30f,30f,Modifier.weight(1f)){vm.updateSelectedBand(gain=it)}
   EditableBox("Q",b.q,"",.1f,40f,Modifier.weight(1f)){vm.updateSelectedBand(q=it)}
  }
  EditableBox("PREAMP",vm.preamp,"dB",-30f,12f,Modifier.fillMaxWidth()){vm.preamp=it}
 }
}

@Composable private fun EditableBox(label:String,value:Float,unit:String,min:Float,max:Float,modifier:Modifier,onSet:(Float)->Unit){
 var show by remember{mutableStateOf(false)}
 Column(modifier.background(Color(0xFF0D0E13),RoundedCornerShape(6.dp)).border(1.dp,ZBR,RoundedCornerShape(6.dp)).clickable{show=true}.padding(7.dp)){
  Text(label,color=ZM,fontSize=8.sp)
  Text("${fmt(value)} $unit",color=ZPK,fontSize=12.sp,fontWeight=FontWeight.Bold)
 }
 if(show) NumberDialog(label,value,min,max,unit,{onSet(it);show=false},{show=false})
}

@Composable private fun NumberDialog(title:String,value:Float,min:Float,max:Float,unit:String,onConfirm:(Float)->Unit,onCancel:()->Unit){
 var text by remember(value){mutableStateOf(fmt(value))}
 AlertDialog(onDismissRequest=onCancel,title={Text(title)},text={OutlinedTextField(value=text,onValueChange={text=it},singleLine=true,label={Text(if(unit.isEmpty())"Valor" else unit)})},confirmButton={TextButton(onClick={text.toFloatOrNull()?.let{onConfirm(it.coerceIn(min,max))}}){Text("OK")}},dismissButton={TextButton(onClick=onCancel){Text("Cancelar")}})
}

@Composable private fun Presets(vm:EqViewModel){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Flat" to {vm.applyPresetFlat()},"Infrabass" to {vm.applyPresetZeusInfrabass()},"Bass Boost" to {vm.applyPresetBassBoost()},"Vocal" to {vm.applyPresetVocalClear()}).forEach{(n,a)->Text(n,color=if(n=="Infrabass")ZP else ZT,fontSize=9.sp,modifier=Modifier.background(ZSUR,RoundedCornerShape(6.dp)).clickable{a()}.padding(horizontal=9.dp,vertical=6.dp))}}}
@Composable private fun Bands(vm:EqViewModel){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){vm.bands.forEachIndexed{i,b->Text("${i+1}\n${if(b.frequency>=1000)(b.frequency/1000).toString()+"k" else b.frequency.toInt().toString()}",color=if(i==vm.selectedBandIndex)Color.Black else ZT,fontSize=8.sp,textAlign=TextAlign.Center,modifier=Modifier.width(42.dp).background(if(i==vm.selectedBandIndex)b.color else ZSUR,RoundedCornerShape(6.dp)).clickable{vm.selectBand(i)}.padding(vertical=5.dp))}}}

private fun uTh(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbThLow=x;1->v.compMbThLoMid=x;2->v.compMbThHiMid=x;3->v.compMbThHigh=x}}
private fun uRa(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbRatioLow=x;1->v.compMbRatioLoMid=x;2->v.compMbRatioHiMid=x;3->v.compMbRatioHigh=x}}
private fun uAt(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbAttackLow=x;1->v.compMbAttackLoMid=x;2->v.compMbAttackHiMid=x;3->v.compMbAttackHigh=x}}
private fun uRe(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbReleaseLow=x;1->v.compMbReleaseLoMid=x;2->v.compMbReleaseHiMid=x;3->v.compMbReleaseHigh=x}}

private fun fmt(v:Float):String = if (kotlin.math.abs(v) >= 1000f) "%.1fk".format(v/1000f) else "%.2f".format(v)
