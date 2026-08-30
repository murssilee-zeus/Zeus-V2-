package com.zeus.v2

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

private val ZBG=Color(0xFF08090C); private val ZSUR=Color(0xFF12131A); private val ZBR=Color(0xFF2A2C36)
private val ZP=Color(0xFFB65CFF); private val ZPK=Color(0xFFFF5AA5); private val ZG=Color(0xFF25D17F)
private val ZT=Color(0xFFF1F1F4); private val ZM=Color(0xFF8D8F9A)

@Composable
fun ZeusStudioScreenV2(vm:EqViewModel,punch:PunchViewModel,onToggleEngine:()->Unit,onSave:()->Unit){
 var page by remember{mutableIntStateOf(0)}
 Column(Modifier.fillMaxSize().background(ZBG).padding(8.dp)){
  Row(Modifier.fillMaxWidth().height(42.dp),verticalAlignment=Alignment.CenterVertically){
   Text("☰",color=ZT,fontSize=22.sp,modifier=Modifier.padding(horizontal=8.dp))
   Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
    Text("ZEUS EQ PRO18",color=ZT,fontSize=20.sp,fontWeight=FontWeight.Bold)
    Text(if(page==0)"EQ / PUNCH" else if(page==1)"DYNAMICS" else "AUTOEQ / PRESETS",color=ZP,fontSize=8.sp)
   }
   Text("⚙",color=ZT,fontSize=22.sp,modifier=Modifier.padding(horizontal=8.dp))
  }
  Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   listOf("1  EQ / PUNCH","2  DYNAMICS","3  AUTOEQ / PRESETS").forEachIndexed{i,label->
    Text(label,color=if(page==i)Color.Black else ZT,fontSize=9.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f).background(if(page==i)ZP else ZSUR,RoundedCornerShape(6.dp)).border(1.dp,if(page==i)ZP else ZBR,RoundedCornerShape(6.dp)).clickable{page=i}.padding(vertical=7.dp))
   }
  }
  Box(Modifier.weight(1f).fillMaxWidth()){
   when(page){0->EqPage(vm,punch);1->DynPage(vm);2->AutoEqPage(vm)}
  }
  Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){
   Text(if(vm.isEngineRunning)"● ACTIVO" else "○ DETENIDO",color=if(vm.isEngineRunning)ZG else ZM,fontSize=9.sp,modifier=Modifier.weight(1f).clickable{onToggleEngine()})
   Text("GUARDAR",color=Color.White,fontSize=9.sp,modifier=Modifier.background(Color(0xFF0E4D3A),RoundedCornerShape(7.dp)).border(1.dp,ZG,RoundedCornerShape(7.dp)).clickable{onSave()}.padding(horizontal=12.dp,vertical=6.dp))
  }
 }
}

@Composable private fun EqPage(vm:EqViewModel,punch:PunchViewModel){
 Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Column(Modifier.weight(1.55f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("SPECTRUM / EQ CURVE",color=ZT,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
    Text("+ BANDA",color=Color.White,fontSize=8.sp,modifier=Modifier.background(ZP,RoundedCornerShape(5.dp)).clickable{vm.addBand()}.padding(horizontal=8.dp,vertical=5.dp))
   }
   EqGraph(vm.bands,vm.selectedBandIndex,vm.spectrum,{vm.selectBand(it)},{i,f,g->vm.selectBand(i);vm.updateSelectedBand(frequency=f,gain=g)},Modifier.fillMaxWidth().height(310.dp))
   Filters(vm); BandEdit(vm); Presets(vm); Bands(vm)
  }
  Column(Modifier.weight(.9f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
   SubSismoCard(vm); PunchCard(punch,vm); Pipe(vm)
  }
 }
}

@Composable private fun DynPage(vm:EqViewModel){
 Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){CompCard(vm)}
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){LimCard(vm)}
 }
}

@Composable private fun AutoEqPage(vm:EqViewModel){
 var query by remember{mutableStateOf("")}
 Card("AUTOEQ · PERFILES DE AUDÍFONOS"){
  Text("Selecciona tus audífonos y aplica automáticamente su perfil paramétrico.",color=ZM,fontSize=10.sp)
  OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,label={Text("Buscar modelo o fabricante")},modifier=Modifier.fillMaxWidth())
  Text("${AutoEqRepository.models(LocalContext.current).size} modelos disponibles",color=ZP,fontSize=9.sp,fontWeight=FontWeight.Bold)
  Column(Modifier.fillMaxWidth().weight(1f,false).heightIn(min=220.dp,max=430.dp).verticalScroll(rememberScrollState())){
   AutoEqRepository.models(LocalContext.current, query).forEach{model->
    Row(Modifier.fillMaxWidth().background(ZSUR,RoundedCornerShape(7.dp)).border(1.dp,ZBR,RoundedCornerShape(7.dp)).padding(9.dp),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text(model.name,color=ZT,fontSize=10.sp);Text("Perfil paramétrico AutoEQ",color=ZM,fontSize=8.sp)}
     Text("APLICAR",color=ZP,fontSize=8.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{
      scope.launch{runCatching{AutoEqRepository.load(LocalContext.current, model)}.getOrNull()?.let{vm.applyAutoEqProfile(it)}}
     }.padding(7.dp))
    }
   }
  }
 }
 SavedPresetsCard(vm)
}
@Composable private fun Card(title:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().background(ZSUR,RoundedCornerShape(10.dp)).border(1.dp,ZBR,RoundedCornerShape(10.dp)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,color=ZP,fontSize=13.sp,fontWeight=FontWeight.Bold);content()}}
@Composable private fun S(label:String,value:Float,range:ClosedFloatingPointRange<Float>,unit:String="",modifier:Modifier=Modifier.fillMaxWidth(),change:(Float)->Unit){Column(modifier){Row{Text(label,color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f));var show by remember{mutableStateOf(false)}; Text(fmt(value)+unit,color=ZT,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{show=true}.padding(4.dp)); if(show){NumberDialog(label,value,range.start,range.endInclusive,unit,{change(it);show=false},{show=false})}};Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=change,valueRange=range,colors=SliderDefaults.colors(thumbColor=ZP,activeTrackColor=ZP,inactiveTrackColor=ZBR))}}

@Composable private fun SubSismoCard(vm:EqViewModel){
 Card("SUB / SISMO (18Hz - 90Hz)"){
  Text("Realce independiente de Punch · graves profundos",color=ZM,fontSize=9.sp)
  S("Power",vm.subBoost,0f..12f," dB"){vm.subBoost=it}
  Text("18 Hz foundation · protected by Headroom + Limiter",color=ZM,fontSize=9.sp)
 }
}

@Composable private fun PunchCard(p:PunchViewModel,vm:EqViewModel){Card("PUNCH (35Hz - 65Hz)"){Text("Post-MBC · 18 Hz stays independent",color=ZM,fontSize=9.sp)
  S("Amount",p.amount,0f..100f," %"){p.updatePunchAmount(it)}
  S("Center",p.centerHz,35f..65f," Hz"){p.updatePunchCenter(it)}
  S("Q",p.q,.5f..3f){p.updatePunchQ(it)}
  S("Headroom trim",vm.headroomTrim,-12f..6f," dB"){vm.headroomTrim=it}
  Text("Punch center and Q are now manual. 18 Hz remains independent.",color=ZM,fontSize=9.sp)}}

@Composable private fun CompCard(vm:EqViewModel){
 var b by remember{mutableIntStateOf(0)}
 val n=listOf("LOW","LO-MID","HI-MID","HIGH")
 val th=listOf(vm.compMbThLow,vm.compMbThLoMid,vm.compMbThHiMid,vm.compMbThHigh)
 val ra=listOf(vm.compMbRatioLow,vm.compMbRatioLoMid,vm.compMbRatioHiMid,vm.compMbRatioHigh)
 val knee=listOf(vm.compMbKneeLow,vm.compMbKneeLoMid,vm.compMbKneeHiMid,vm.compMbKneeHigh)
 val at=listOf(vm.compMbAttackLow,vm.compMbAttackLoMid,vm.compMbAttackHiMid,vm.compMbAttackHigh)
 val re=listOf(vm.compMbReleaseLow,vm.compMbReleaseLoMid,vm.compMbReleaseHiMid,vm.compMbReleaseHigh)
 val pre=listOf(vm.compMbPreGainLow,vm.compMbPreGainLoMid,vm.compMbPreGainHiMid,vm.compMbPreGainHigh)
 val post=listOf(vm.compMbPostGainLow,vm.compMbPostGainLoMid,vm.compMbPostGainHiMid,vm.compMbPostGainHigh)
 Card("COMPRESOR MULTIBANDA"){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){n.forEachIndexed{i,x->Text(x,color=if(b==i)Color.Black else ZT,fontSize=8.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f).background(if(b==i)ZP else Color(0xFF1A1B22),RoundedCornerShape(5.dp)).clickable{b=i}.padding(vertical=6.dp))}}
  Row(Modifier.fillMaxWidth().height(72.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
   listOf("20Hz–120Hz","120Hz–1.2k","1.2k–8k","8k–20k").forEachIndexed{i,label->Box(Modifier.weight(1f).fillMaxHeight().background(if(i==b)ZP.copy(alpha=.22f) else ZSUR,RoundedCornerShape(5.dp)).border(1.dp,if(i==b)ZP else ZBR,RoundedCornerShape(5.dp)).clickable{b=i},contentAlignment=Alignment.Center){Text(label,color=if(i==b)ZP else ZM,fontSize=8.sp,textAlign=TextAlign.Center)}}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   S("PRE",pre[b],-12f..12f," dB",Modifier.weight(1f)){uPre(vm,b,it)}
   S("THRESHOLD",th[b],-60f..0f," dB",Modifier.weight(1f)){uTh(vm,b,it)}
   S("POST",post[b],-12f..12f," dB",Modifier.weight(1f)){uPost(vm,b,it)}
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   S("RATIO",ra[b],1f..20f," :1",Modifier.weight(1f)){uRa(vm,b,it)}
   S("KNEE",knee[b],0f..20f," dB",Modifier.weight(1f)){uKnee(vm,b,it)}
   S("ATTACK",at[b],1f..100f," ms",Modifier.weight(1f)){uAt(vm,b,it)}
   S("RELEASE",re[b],20f..500f," ms",Modifier.weight(1f)){uRe(vm,b,it)}
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   S("CROSS 1",vm.crossoverFrequencies[0],40f..1000f," Hz",Modifier.weight(1f)){vm.setCrossover(0,it)}
   S("CROSS 2",vm.crossoverFrequencies[1],100f..5000f," Hz",Modifier.weight(1f)){vm.setCrossover(1,it)}
   S("CROSS 3",vm.crossoverFrequencies[2],1000f..19500f," Hz",Modifier.weight(1f)){vm.setCrossover(2,it)}
  }
  Row(verticalAlignment=Alignment.CenterVertically){Text("COMPRESSOR",color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f));Switch(checked=vm.compressorMultibandEnabled,onCheckedChange={vm.compressorMultibandEnabled=it})}
 }
}

@Composable private fun LimCard(vm:EqViewModel){Card("LIMITADOR"){S("Threshold",vm.limiterThreshold,-12f..0f," dB"){vm.limiterThreshold=it};S("Attack",vm.limiterAttack,.1f..20f," ms"){vm.limiterAttack=it};S("Release",vm.limiterRelease,20f..500f," ms"){vm.limiterRelease=it};S("Ratio",vm.limiterRatio,1f..30f," :1"){vm.limiterRatio=it};S("Post Gain",vm.limiterPostGain,-12f..12f," dB"){vm.limiterPostGain=it};Row(verticalAlignment=Alignment.CenterVertically){Text("Activado",color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f));Switch(checked=vm.limiterEnabled,onCheckedChange={vm.limiterEnabled=it})}}}
@Composable private fun Pipe(vm:EqViewModel){Card("AUDIO EFFECTS PIPELINE"){Text("1  MBC · Procesamiento multibanda",color=ZT,fontSize=10.sp);Text("2  PUNCH 35-65 Hz · Controlado de subgraves",color=ZT,fontSize=10.sp);Text("3  LIMITER · Protección final de picos",color=ZT,fontSize=10.sp);Text("DIRECT OUTPUT · ACTIVO",color=ZG,fontSize=10.sp,fontWeight=FontWeight.Bold)}}

@Composable private fun Filters(vm:EqViewModel){val b=vm.selectedBand();val t=listOf(EqBand.FilterType.PEAK to "PEAK",EqBand.FilterType.LOW_SHELF to "LOW",EqBand.FilterType.HIGH_SHELF to "HIGH",EqBand.FilterType.LOW_PASS to "LPF",EqBand.FilterType.HIGH_PASS to "HPF",EqBand.FilterType.BYPASS to "BYPASS");Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){t.forEach{(x,l)->Text(l,color=if(b?.filterType==x)Color.Black else ZT,fontSize=8.sp,modifier=Modifier.background(if(b?.filterType==x) ZPK else ZSUR,RoundedCornerShape(6.dp)).border(1.dp,ZBR,RoundedCornerShape(6.dp)).clickable{vm.updateSelectedBand(filterType=x)}.padding(horizontal=8.dp,vertical=6.dp))}}}
@Composable
private fun BandEdit(vm: EqViewModel) {
    val b = vm.selectedBand() ?: return
    Card("BANDA ${vm.selectedBandIndex + 1}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EditableBox("FREQ", b.frequency, "Hz", 1f, 30000f, Modifier.weight(1f)) { vm.updateSelectedBand(frequency = it) }
            EditableBox("GAIN", b.gain, "dB", -30f, 30f, Modifier.weight(1f)) { vm.updateSelectedBand(gain = it) }
            EditableBox("Q", b.q, "", .1f, 40f, Modifier.weight(1f)) { vm.updateSelectedBand(q = it) }
        }
        EditableBox("PREAMP", vm.preamp, "dB", -30f, 12f, Modifier.fillMaxWidth()) { vm.preamp = it }
    }
}

@Composable
private fun EditableBox(label:String,value:Float,unit:String,min:Float,max:Float,modifier:Modifier,onSet:(Float)->Unit){
    var show by remember { mutableStateOf(false) }
    Column(modifier.background(Color(0xFF0D0E13),RoundedCornerShape(6.dp)).border(1.dp,ZBR,RoundedCornerShape(6.dp)).clickable{show=true}.padding(7.dp)){
        Text(label,color=ZM,fontSize=8.sp)
        Text("${fmt(value)} $unit",color=ZPK,fontSize=12.sp,fontWeight=FontWeight.Bold)
    }
    if(show) NumberDialog(label,value,min,max,unit,{v->onSet(v);show=false},{show=false})
}

@Composable
private fun NumberDialog(title:String,value:Float,min:Float,max:Float,unit:String,onConfirm:(Float)->Unit,onCancel:()->Unit){
    var text by remember(value){mutableStateOf(fmt(value))}
    AlertDialog(onDismissRequest=onCancel,title={Text(title)},text={OutlinedTextField(value=text,onValueChange={text=it},singleLine=true,label={Text(if(unit.isEmpty())"Valor" else unit)})},confirmButton={TextButton(onClick={text.toFloatOrNull()?.let{onConfirm(it.coerceIn(min,max))}}){Text("OK")}},dismissButton={TextButton(onClick=onCancel){Text("Cancelar")}})
}

@Composable private fun Presets(vm:EqViewModel){
 Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   listOf("Flat" to {vm.applyPresetFlat()},"Infrabass" to {vm.applyPresetZeusInfrabass()},"Bass Boost" to {vm.applyPresetBassBoost()},"Vocal" to {vm.applyPresetVocalClear()}).forEach{(n,a)->Text(n,color=if(n=="Infrabass")ZP else ZT,fontSize=9.sp,modifier=Modifier.background(ZSUR,RoundedCornerShape(6.dp)).clickable{a()}.padding(horizontal=9.dp,vertical=6.dp))}
  }
  SavedPresetsCard(vm)
 }
}
@Composable private fun Bands(vm:EqViewModel){
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){
   vm.bands.forEachIndexed{i,b->Text("${i+1}\n${if(b.frequency>=1000)(b.frequency/1000).toString()+"k" else b.frequency.toInt().toString()}",color=if(i==vm.selectedBandIndex)Color.Black else ZT,fontSize=8.sp,textAlign=TextAlign.Center,modifier=Modifier.width(42.dp).background(if(i==vm.selectedBandIndex)b.color else ZSUR,RoundedCornerShape(6.dp)).clickable{vm.selectBand(i)}.padding(vertical=5.dp))}
  }
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
   Text("BANDAS ${vm.bands.size}/${EqViewModel.MAX_BANDS}",color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f))
   Text("−",color=ZT,fontSize=18.sp,modifier=Modifier.background(ZSUR,RoundedCornerShape(6.dp)).clickable{vm.removeSelectedBand()}.padding(horizontal=10.dp,vertical=2.dp))
   Text("+ BANDA",color=Color.White,fontSize=9.sp,modifier=Modifier.background(ZP,RoundedCornerShape(6.dp)).clickable{vm.addBand()}.padding(horizontal=9.dp,vertical=6.dp))
  }
 }
}

@Composable private fun SavedPresetsCard(vm:EqViewModel){
 var names by remember { mutableStateOf(vm.namedPresetNames()) }
 var showSave by remember { mutableStateOf(false) }
 var name by remember { mutableStateOf("") }
 Card("MIS CONFIGURACIONES"){
  Row(verticalAlignment=Alignment.CenterVertically){
   Text("Guardar configuración con nombre",color=ZM,fontSize=9.sp,modifier=Modifier.weight(1f))
   Text("+ GUARDAR",color=Color.White,fontSize=9.sp,modifier=Modifier.background(ZG,RoundedCornerShape(6.dp)).clickable{showSave=true}.padding(horizontal=8.dp,vertical=6.dp))
  }
  names.forEach { preset ->
   Row(verticalAlignment=Alignment.CenterVertically){
    Text(preset,color=ZT,fontSize=9.sp,modifier=Modifier.weight(1f).clickable{vm.loadNamedPreset(preset)})
    Text("CARGAR",color=ZP,fontSize=8.sp,modifier=Modifier.clickable{vm.loadNamedPreset(preset)}.padding(4.dp))
    Text("×",color=ZPK,fontSize=14.sp,modifier=Modifier.clickable{vm.deleteNamedPreset(preset);names=vm.namedPresetNames()}.padding(4.dp))
   }
  }
  if(names.isEmpty()) Text("Aún no hay configuraciones guardadas.",color=ZM,fontSize=8.sp)
 }
 if(showSave){
  AlertDialog(onDismissRequest={showSave=false},title={Text("Guardar configuración")},text={OutlinedTextField(value=name,onValueChange={name=it},singleLine=true,label={Text("Nombre")})},
   confirmButton={TextButton(onClick={if(name.trim().isNotEmpty()){vm.saveNamedPreset(name);names=vm.namedPresetNames();name="";showSave=false}}){Text("Guardar")}},
   dismissButton={TextButton(onClick={showSave=false}){Text("Cancelar")}})
 }
}

@Composable private fun AutoEqCard(vm:EqViewModel){
 var show by remember { mutableStateOf(false) }
 var query by remember { mutableStateOf("") }
 var loading by remember { mutableStateOf(false) }
 var error by remember { mutableStateOf<String?>(null) }
 val scope=rememberCoroutineScope()
 Card("AUTOEQ"){
  Row(verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("Corrección de auriculares basada en AutoEQ",color=ZM,fontSize=9.sp)
    Text("${AutoEqRepository.models(LocalContext.current).size} modelos disponibles · perfil paramétrico",color=ZT,fontSize=9.sp)
   }
   Text("VER MODELOS",color=Color.White,fontSize=9.sp,modifier=Modifier.background(ZP,RoundedCornerShape(6.dp)).clickable{show=true;query="";error=null}.padding(horizontal=8.dp,vertical=6.dp))
  }
  if(error!=null) Text(error!!,color=ZPK,fontSize=8.sp)
 }
 if(show){
  AlertDialog(onDismissRequest={if(!loading)show=false},title={Text("AutoEQ · Lista de modelos")},text={
   Column(Modifier.heightIn(max=430.dp)){
    OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,label={Text("Buscar modelo")},modifier=Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)){
     items(AutoEqRepository.models(LocalContext.current, query)){model->
      Row(Modifier.fillMaxWidth().clickable{
       if(!loading){loading=true;error=null;scope.launch{try{val profile=AutoEqRepository.load(LocalContext.current, model);vm.applyAutoEqProfile(profile);show=false}catch(t:Throwable){error=t.message?:"No se pudo cargar AutoEQ"}finally{loading=false}}}
      }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
       Text(model.name,color=ZT,fontSize=9.sp,modifier=Modifier.weight(1f))
       Text(if(loading)"..." else "APLICAR",color=ZP,fontSize=8.sp)
      }
     }
    }
   }
  },confirmButton={TextButton(onClick={if(!loading)show=false}){Text(if(loading)"Cargando..." else "Cerrar")}})
 }
}

private fun uPre(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbPreGainLow=x;1->v.compMbPreGainLoMid=x;2->v.compMbPreGainHiMid=x;3->v.compMbPreGainHigh=x}}
private fun uPost(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbPostGainLow=x;1->v.compMbPostGainLoMid=x;2->v.compMbPostGainHiMid=x;3->v.compMbPostGainHigh=x}}
private fun uTh(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbThLow=x;1->v.compMbThLoMid=x;2->v.compMbThHiMid=x;3->v.compMbThHigh=x}}
private fun uRa(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbRatioLow=x;1->v.compMbRatioLoMid=x;2->v.compMbRatioHiMid=x;3->v.compMbRatioHigh=x}}
private fun uKnee(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbKneeLow=x;1->v.compMbKneeLoMid=x;2->v.compMbKneeHiMid=x;3->v.compMbKneeHigh=x}}
private fun uAt(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbAttackLow=x;1->v.compMbAttackLoMid=x;2->v.compMbAttackHiMid=x;3->v.compMbAttackHigh=x}}
private fun uRe(v:EqViewModel,i:Int,x:Float){when(i){0->v.compMbReleaseLow=x;1->v.compMbReleaseLoMid=x;2->v.compMbReleaseHiMid=x;3->v.compMbReleaseHigh=x}}

private fun fmt(v:Float):String = if (kotlin.math.abs(v) >= 1000f) "%.1fk".format(v/1000f) else "%.2f".format(v)
