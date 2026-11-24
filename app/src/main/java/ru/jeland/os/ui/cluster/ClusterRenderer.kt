package ru.jeland.os.ui.cluster

import android.content.res.Resources
import android.graphics.*
import ru.jeland.os.core.*
import java.util.Locale
import kotlin.math.*

/** Native housing and live vector readouts, in 2048×768 asset coordinates. */
internal class ClusterRenderer(private val resources:Resources) {
    private var labelsForNativeEnglish=false

    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaints=mutableMapOf<Triple<Float,Int,Boolean>,Paint>()
    private val textBounds=Rect()
    private val regular=Typeface.create("sans-serif",Typeface.NORMAL)
    private val numeric=Typeface.create("sans-serif-medium",Typeface.ITALIC)
    private val scaleTypeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private var bitmap:Bitmap?=null
    private var key=""
    private val housing=SculptedClusterHousing(resources)
    private val oem=ClusterOemAssets(resources)
    private val warningStrip=ClusterWarningStrip(oem)
    private val island=ClusterIslandPainter(oem)
    private val arcSegments=mutableMapOf<Pair<ClusterDial,Float>,List<Path>>()
    private val verticalSegments=mutableMapOf<Boolean,List<Path>>()
    private val thermalArcFills=mutableMapOf<Pair<ClusterDial,Float>,Pair<Float,List<Path>>>()
    private var thermalSideFill:Pair<Float,List<Path>>?=null
    private var dark=true
    private var sportDesign=false
    private var startupNeedles:ClusterStartupNeedles?=null
    private var cameraTitle:String?=null
    private var cameraLimit:Int?=null
    private var white=Color.WHITE
    private var muted=Color.LTGRAY
    private var blue=Color.CYAN
    private val red=Color.rgb(247,43,59)
    fun close(){bitmap=null;key="";oem.close();housing.close();textPaints.clear()}
    private fun ink(color:Int,width:Float=0f,shader:Shader?=null)=paint.apply {
        reset();isAntiAlias=true;this.color=color;strokeWidth=width;style=if(width>0)Paint.Style.STROKE else Paint.Style.FILL
        strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.BUTT;this.shader=shader
    }
    private fun alpha(color:Int,a:Int)=Color.argb(a,Color.red(color),Color.green(color),Color.blue(color))
    private fun text(c:Canvas,value:String,x:Float,y:Float,size:Float,tint:Int=white,digits:Boolean=false,maxWidth:Float?=null,centerY:Float?=null) {
        val p=textPaints.getOrPut(Triple(size,tint,digits)) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=tint;textSize=size;typeface=if(digits)numeric else regular
                textAlign=if(digits)Paint.Align.LEFT else Paint.Align.CENTER
            }
        }
        val display=if(maxWidth!=null&&p.measureText(value)>maxWidth){
            var end=value.length;while(end>0&&p.measureText(value.take(end)+"…")>maxWidth)end--;value.take(end)+"…"
        }else value
        val baseline=if(centerY!=null) {
            p.getTextBounds(display,0,display.length,textBounds)
            centerY-(textBounds.top+textBounds.bottom)/2f
        }else y
        if(digits) {
            p.getTextBounds(display,0,display.length,textBounds)
            c.drawText(display,x-(textBounds.left+textBounds.right)/2f,baseline,p)
        }else c.drawText(display,x,baseline,p)
    }
    private fun fmt(n:Double?,places:Int=0)=n?.takeIf {it.isFinite()}?.let {String.format(Locale.getDefault(),"%.${places}f",it)}?:"—"
    private fun line(c:Canvas,x:Float,y:Float,x2:Float,y2:Float,color:Int,width:Float=1f){c.drawLine(x,y,x2,y2,ink(color,width))}
    private fun point(d:ClusterDial,r:Float,angle:Float):PointF {
        val a=Math.toRadians(angle.toDouble());return PointF(d.x+d.rx*r*cos(a).toFloat(),d.y+d.ry*r*sin(a).toFloat())
    }
    private fun bounds(d:ClusterDial,r:Float)=RectF(d.x-d.rx*r,d.y-d.ry*r,d.x+d.rx*r,d.y+d.ry*r)
    private fun arc(c:Canvas,d:ClusterDial,r:Float,start:Float,sweep:Float,tint:Int,width:Float){c.drawArc(bounds(d,r),start,sweep,false,ink(tint,width))}
    fun draw(c:Canvas,s:ClusterPanelState,design:ClusterDesign,isDark:Boolean,clock:String,l:ClusterLabels,
             animatedSpeed:Float?,animatedRpm:Float?,miniature:Boolean=false,
             accent:ClusterAccent=ClusterAccent.CYAN,beltVisible:Boolean=true,startup:ClusterStartupNeedles?=null,includeMessage:Boolean=true,roadAlert:RoadAlert?=null,navigationMode:ClusterNavigationMode=ClusterNavigationMode.MAP) {
        val currentTitle=if(miniature)null else roadAlert?.title
        if(currentTitle!=cameraTitle){cameraTitle=currentTitle;cameraLimit=currentTitle?.let {RoadAlertSymbol.cameraSpeedLimit(it)}}
        startupNeedles=startup
        sportDesign=design==ClusterDesign.SPORT
        dark=isDark;white=if(dark)Color.rgb(245,250,255)else Color.rgb(13,29,42)
        muted=if(dark)Color.rgb(157,206,233)else Color.rgb(54,84,106)
        blue=if(dark)accent.darkArgb else accent.lightArgb
        c.save();c.scale(1920f/2048f,720f/768f)
        if(design==ClusterDesign.ORIGINAL){
            c.drawColor(if(dark)Color.rgb(5,13,20)else Color.rgb(236,243,247))
            text(c,"Original",1024f,330f,72f)
            text(c,l.stock,1024f,408f,34f,muted)
            c.restore();return
        }
        val cacheKey="${design.name}:$dark:${accent.name}:$miniature:$navigationMode:${s.activeNavigation!=null}"
        if(key!=cacheKey){
            bitmap=Bitmap.createBitmap(2048,768,Bitmap.Config.ARGB_8888).also {
                housing.draw(Canvas(it),design,dark,blue,miniature,navigationMode,s.activeNavigation!=null)
            }
            key=cacheKey
        }
        c.save()
        val p=ink(Color.WHITE);p.isFilterBitmap=true
        bitmap?.let {c.drawBitmap(it,null,RectF(0f,0f,2048f,768f),p)}
        c.restore()
        labelsForNativeEnglish=l.km=="km"

        when(design){
            ClusterDesign.CLASSIC->{
                val (left,right)=ClusterGeometry.dials(design)
                gauge(c,left,s.speed,animatedSpeed,false,l);gauge(c,right,s.rpm,animatedRpm,true,l)
                secondary(c,left,s,false,l);secondary(c,right,s,true,l)
                classicCenter(c,s,l,miniature,navigationMode)
            }
            ClusterDesign.NAVIGATION->{
                val (left,right)=ClusterGeometry.dials(design)
                gauge(c,left,s.speed,animatedSpeed,false,l);gauge(c,right,s.rpm,animatedRpm,true,l)
                secondary(c,left,s,false,l);secondary(c,right,s,true,l)
                val nav=s.activeNavigation

                if(nav?.demo==true)text(c,"DEMO",430f,130f,16f,muted,maxWidth=70f)
                val mapGuidance=navigationMode==ClusterNavigationMode.MAP&&nav!=null
                if(mapGuidance) {
                    arrow(c,94f,124f,80f,nav)
                    text(c,distance(nav,l),320f,174f,46f,white,maxWidth=230f)
                    text(c,nav.street,278f,226f,28f,muted,maxWidth=350f)
                }
                if(navigationMode==ClusterNavigationMode.GUIDANCE)navigationPanel(c,nav,l,1024f,220f,700f)
                val values=listOf(fmt(s.consumption,1),duration(s.tripMillis),fmt(s.tripKm,1))
                val units=listOf(l.consumption,l.hour,l.km)
                values.forEachIndexed {index,value->
                    val x=144f+index*132f
                    text(c,value,x,if(mapGuidance)294f else 148f,27f,white,true,maxWidth=122f)
                    text(c,units[index],x,if(mapGuidance)328f else 182f,20f,muted,maxWidth=122f)
                }
                if(!miniature&&navigationMode==ClusterNavigationMode.MAP)text(c,"\u00a9 OpenStreetMap \u00b7 CoMaps",1024f,686f,12f,muted,maxWidth=300f)

            }
            ClusterDesign.SPORT->{
                val dial=ClusterGeometry.dials(design).single()
                gauge(c,dial,s.rpm,animatedRpm,true,l,sport=true)
                text(c,fmt(s.speed),dial.x,dial.y+dial.ry*.72f,106f,white,true)
                text(c,l.kmh,dial.x,dial.y+dial.ry*.82f,25f,muted)

                c.save();c.clipRect(190f,114f,688f,626f)
                text(c,l.sinceStart,465f,0f,32f,white,maxWidth=360f,centerY=(151f+194f)/2)
                line(c,220f,195f,664f,195f,alpha(blue,130),1.2f)
                val values=listOf(fmt(s.consumption,1),fmt(s.tripKm,1),duration(s.tripMillis))
                val captions=if(labelsForNativeEnglish)listOf("Consumption","Distance","Time")else listOf("Расход","Пробег","Время")
                val units=listOf(l.consumption,l.km,l.hour)
                val icons=listOf(0,2,1)
                values.forEachIndexed {index,value->
                    val y=254f+index*100f
                    metricIcon(c,icons[index],258f,y+4f)
                    text(c,captions[index],459f,y-20f,28f,muted,maxWidth=310f)
                    text(c,"$value ${units[index]}",459f,y+30f,44f,white,true,maxWidth=326f)
                    line(c,220f,y+54f,664f,y+54f,alpha(blue,75))
                }
                c.restore()
                navigationWidget(c,s.activeNavigation,l,design,miniature,navigationMode)

                sideScale(c,s.fuel,false);sideScale(c,s.coolantLevel,true)
                fuelIcon(c,90f,169f,1f,reserve=ClusterLamp.RESERVE in s.lamps)
                thermometer(c,1932f,167f,1f,s.coolantCondition)
                text(c,"F",143f,257f,25f);text(c,"E",143f,568f,25f)
                text(c,"H",1909f,257f,25f);text(c,"C",1909f,568f,25f)
                text(c,"${fmt(s.rangeKm)} ${l.km}",111f,620f,26f,muted,maxWidth=174f)
                val coolantText=coolantLabel(s)
                if(coolantText.isNotEmpty())text(c,coolantText,1815f,620f,26f,muted,maxWidth=220f)

            }
            else->Unit
        }
        cameraLimit?.let {limit->clusterSpeedLimitCircle(design)?.let {sign->speedLimitSign(c,sign,limit)}}
        header(c,s,clock,beltVisible,design)
        footer(c,s,l)
        if(includeMessage) {
            warningStrip.drawIndicators(c,s,white,l,design)
            val notice=clusterIslandNotice(s,l,roadAlert);val target=island.target(notice)
            island.draw(c,s,clock,dark,blue,l,target.first,target.second,if(notice==null)0f else 1f,notice,readouts=true)
        }
        c.restore()
    }
    private fun speedLimitSign(c:Canvas,sign:ClusterSpeedLimitCircle,limit:Int) {
        val (x,y,r)=sign
        c.drawCircle(x,y,r,ink(Color.WHITE))
        val border=r*.16f
        c.drawCircle(x,y,r-border/2,ink(Color.rgb(232,39,49),border))
        val number=limit.toString()
        val digits=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.rgb(16,20,25);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            textSize=r*1.25f;textAlign=Paint.Align.CENTER
        }
        digits.getTextBounds(number,0,number.length,textBounds)
        if(textBounds.width()>r*1.44f)digits.textSize*=r*1.44f/textBounds.width()
        digits.getTextBounds(number,0,number.length,textBounds)
        c.drawText(number,x,y-(textBounds.top+textBounds.bottom)/2f,digits)
    }
    private fun metricIcon(c:Canvas,index:Int,x:Float,y:Float) {
        when(index) {
            0->fuelIcon(c,x-20f,y-6f,1.10f,neutralTint=muted)
            1->clockIcon(c,x-22f,y-6f,1.45f,muted)
            else->roadIcon(c,x-22f,y-6f,1.45f,muted)
        }
    }
    private fun metricsRow(c:Canvas,s:ClusterPanelState,l:ClusterLabels,x:Float,iconY:Float,spacing:Float) {
        val values=listOf(fmt(s.consumption,1),duration(s.tripMillis),fmt(s.tripKm,1))
        val units=listOf(l.consumption,l.hour,l.km)
        values.forEachIndexed {index,value->
            val column=x+(index-1)*spacing
            val icon=when(index){0->ClusterOemIcon.FUEL_NORMAL;1->ClusterOemIcon.CLOCK;else->ClusterOemIcon.ROAD}
            oem.draw(c,icon,column-21f,iconY,42f,neutralTint=muted,centerVisible=true)
            text(c,value,column,0f,48f,if(value=="\u2014")muted else white,maxWidth=spacing-8f,centerY=iconY+70f)
            text(c,units[index],column,0f,25f,muted,maxWidth=spacing-8f,centerY=iconY+112f)
        }
    }
    private fun navigationPanel(c:Canvas,navigation:NavigationState?,l:ClusterLabels,x:Float,top:Float,width:Float,compact:Boolean=false) {
        val size=if(compact)136f else 150f
        if(navigation!=null) {
            arrow(c,x-size/2,top,size,navigation)
            text(c,distance(navigation,l),x,top+if(compact)175f else 200f,if(compact)48f else 54f,white,maxWidth=width)
            text(c,navigation.street,x,top+if(compact)216f else 251f,30f,muted,maxWidth=width)
        }
    }
    private fun classicCenter(c:Canvas,s:ClusterPanelState,l:ClusterLabels,miniature:Boolean,navigationMode:ClusterNavigationMode) {
        c.save();c.clipRect(744f,96f,1304f,614f)
        text(c,l.sinceStart,1024f,135f,32f,muted,maxWidth=525f)
        metricsRow(c,s,l,1024f,146f,168f)
        navigationWidget(c,s.activeNavigation,l,ClusterDesign.CLASSIC,miniature,navigationMode)
        c.restore()
    }
    private fun navigationWidget(c:Canvas,nav:NavigationState?,l:ClusterLabels,design:ClusterDesign,miniature:Boolean,mode:ClusterNavigationMode) {
        if(mode==ClusterNavigationMode.MAP)miniMapPanel(c,nav,l,design,miniature)
        else {
            val b=clusterMapBounds(design,false)?:return
            navigationPanel(c,nav,l,b.left+b.width/2,b.top+(b.height-281f).coerceAtLeast(0f)/2,b.width-24f,
                compact=design==ClusterDesign.CLASSIC)
        }
    }
    private fun miniMapPanel(c:Canvas,nav:NavigationState?,l:ClusterLabels,design:ClusterDesign,miniature:Boolean) {
        val b=clusterMapBounds(design,nav!=null)?:return
        val top=if(nav!=null)b.top-46f else b.top
        val base=if(dark)Color.rgb(7,19,30)else Color.rgb(231,239,244)
        c.drawRoundRect(b.left,top,b.left+b.width,b.top+b.height,16f,16f,ink(base))
        c.drawRoundRect(b.left,top,b.left+b.width,b.top+b.height,16f,16f,ink(alpha(muted,110),1f))
        if(miniature) {
            c.save();c.clipRect(b.left+3,b.top,b.left+b.width-3,b.top+b.height-10)
            for(i in -1..5)line(c,b.left+i*130,b.top,b.left+i*130+85,b.top+b.height,alpha(muted,40),6f)
            for(i in 0..3)line(c,b.left,b.top+35+i*70,b.left+b.width,b.top+10+i*70,alpha(muted,40),6f)
            c.restore()
        }
        if(nav!=null) {
            if(nav.demo)text(c,"DEMO",b.left+28f,top-8f,13f,muted,maxWidth=52f)
            arrow(c,b.left+14,top+8,30f,nav,relief=false)
            text(c,distance(nav,l),b.left+120,top+32,27f,white,maxWidth=138f)
            line(c,b.left+202,top+10,b.left+202,top+35,alpha(muted,80))
            text(c,nav.street,b.left+210+(b.width-226)/2,top+31,23f,muted,maxWidth=b.width-230)
        }
        if(!miniature)text(c,"© OpenStreetMap · CoMaps",b.left+b.width/2,b.top+b.height+10f,10f,muted,maxWidth=b.width)
    }
    private fun gauge(c:Canvas,d:ClusterDial,value:Double?,animated:Float?,tach:Boolean,l:ClusterLabels,sport:Boolean=false) {
        val start=ClusterDialMetrics.START
        val sweep=ClusterDialMetrics.SWEEP
        val outer=ClusterDialMetrics.SCALE_OUTER
        val inner=ClusterDialMetrics.SCALE_INNER
        if(tach){
            val thickness=.054f
            arc(c,d,outer-thickness/2,start+sweep*.75f,sweep*.25f,alpha(red,65),d.rx*thickness)
            arc(c,d,outer,start+sweep*.75f,sweep*.25f,red,2f)
        }
        val level=((if(tach)startupNeedles?.rpm?:animated?:0f else startupNeedles?.speed?:animated?:0f)/(if(tach)8000 else 220)).coerceIn(0f,1f)
        val end=start+sweep*level
        if(level>0){
            val path=Path().apply {arcTo(bounds(d,outer),start,sweep*level);arcTo(bounds(d,inner),end,-sweep*level);close()}
            val gradient=RadialGradient(d.x,d.y,d.rx*.86f,intArrayOf(Color.TRANSPARENT,alpha(blue,12),alpha(blue,90),alpha(blue,220)),floatArrayOf(0f,.6f,.89f,1f),Shader.TileMode.CLAMP)
            c.drawPath(path,ink(blue,shader=gradient));arc(c,d,outer,start,sweep*level,blue,2f)
            if(sport) {
                val needle=Path().apply {
                    val a=point(d,.33f,end-2f);val b=point(d,outer,end);val e=point(d,.33f,end+2f)
                    moveTo(a.x,a.y);lineTo(b.x,b.y);lineTo(e.x,e.y);close()
                }
                c.drawPath(needle,ink(white,shader=LinearGradient(d.x,d.y,point(d,outer,end).x,point(d,outer,end).y,
                    intArrayOf(alpha(blue,100),blue,white),null,Shader.TileMode.CLAMP)))
            }
            val tip=point(d,outer,end);val heel=point(d,inner,end)
            line(c,tip.x,tip.y,heel.x,heel.y,alpha(blue,65),6f);line(c,tip.x,tip.y,heel.x,heel.y,Color.WHITE,2f)
        }
        val majorCount=if(tach)8 else 11
        val divisions=majorCount*4
        for(i in 0..divisions){
            val major=i%4==0
            val angle=start+sweep*i/divisions
            val outside=point(d,outer,angle)
            val inside=point(d,if(major){ClusterDialMetrics.MAJOR_TICK_INNER}
                else ClusterDialMetrics.MINOR_TICK_INNER,angle)
            line(c,outside.x,outside.y,inside.x,inside.y,
                if(tach&&i>=divisions*.75f)red else if(major)white else muted,
                if(major){if(sport)4.2f else 2.8f}else if(sport)1.5f else 1.1f)
            if(major){
                val at=point(d,ClusterDialMetrics.NUMBER_RADIUS,angle)
                val size=d.ry*(if(sport).130f else ClusterDialMetrics.NUMBER_SIZE)
                val label=if(tach)"${i/4}"else"${i/4*20}"
                val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=white;textSize=size;typeface=if(sport)numeric else scaleTypeface}
                p.getTextBounds(label,0,label.length,textBounds)
                c.drawText(label,at.x-(textBounds.left+textBounds.right)/2f,at.y-(textBounds.top+textBounds.bottom)/2f,p)
            }
        }
        val font=if(sport)98f else d.ry*.365f
        text(c,fmt(if(tach)value?.let {(animated?:it.toFloat())/1000.0}else value,if(tach)1 else 0),d.x,d.y+d.ry*.135f,font,white,true,maxWidth=d.rx*.96f)
        text(c,if(tach)l.rpm else l.kmh,d.x,d.y+d.ry*.30f,d.ry*.097f,muted,maxWidth=d.rx*.98f)
    }
    private fun secondary(c:Canvas,d:ClusterDial,s:ClusterPanelState,temp:Boolean,l:ClusterLabels) {
        val radius=d.rx
        val track=ClusterDial(d.x,d.y,radius*ClusterDialMetrics.SECONDARY_RADIUS,radius*ClusterDialMetrics.SECONDARY_RADIUS)
        val paths=arcSegments.getOrPut(track to radius) {
            ClusterSegments.build(8,radius*ClusterDialMetrics.SECONDARY_THICKNESS,radius*ClusterDialMetrics.SECONDARY_GAP) {t->
                point(track,1f,ClusterDialMetrics.SECONDARY_START+ClusterDialMetrics.SECONDARY_SWEEP*t)
            }
        }
        val level=if(temp)s.coolantLevel else s.fuel
        val active=level?.let {ceil(it.coerceIn(0.0,1.0)*8).toInt()}?:0
        paths.forEachIndexed {i,path->
            val critical=if(temp)i==7 else i==0
            val tint=when {critical->red;!temp&&i<active->blue
                else->if(dark)Color.rgb(39,55,66)else Color.rgb(178,192,202)}
            c.drawPath(path,ink(tint))
        }
        if(temp) {
            // Use the continuous native progress, including a partially filled final segment.
            val progress=level?.toFloat()?.coerceIn(0f,1f)?:0f
            val key=track to radius
            val partial=thermalArcFills[key]?.takeIf {it.first==progress}?.second?:
                ClusterSegments.build(8,radius*ClusterDialMetrics.SECONDARY_THICKNESS,radius*ClusterDialMetrics.SECONDARY_GAP,progress) {t->
                    point(track,1f,ClusterDialMetrics.SECONDARY_START+ClusterDialMetrics.SECONDARY_SWEEP*t)
                }.also {thermalArcFills[key]=progress to it}
            partial.forEachIndexed {i,path->c.drawPath(path,ink(if(i==7||s.coolantCondition==ClusterCoolantCondition.HOT)red else blue))}
        }
        val label=if(temp)coolantLabel(s)else "${fmt(s.rangeKm)} ${l.km}"
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=white;textSize=radius*.108f;typeface=regular}
        val iconSize=radius*.138f;val gap=if(label.isEmpty())0f else radius*.035f;val maxText=radius*.86f-iconSize-gap
        if(p.measureText(label)>maxText)p.textSize*=maxText/p.measureText(label)
        val width=p.measureText(label);val start=d.x-(iconSize+gap+width)/2
        val centre=d.y+radius*.56f
        val icon=if(temp)ClusterOemIcon.coolant(s.coolantCondition)
            else if(ClusterLamp.RESERVE in s.lamps)ClusterOemIcon.FUEL_LOW else ClusterOemIcon.FUEL_NORMAL
        oem.draw(c,icon,start,centre-iconSize/2,iconSize,neutralTint=white)
        p.getTextBounds(label,0,label.length,textBounds)
        c.drawText(label,start+iconSize+gap,centre-(textBounds.top+textBounds.bottom)/2f,p)
        for((angle,value) in listOf(128f to if(temp)"C"else"E",52f to if(temp)"H"else"F")) {
            val at=point(d,.80f,angle)
            val q=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=muted;textSize=radius*.072f;typeface=regular}
            q.getTextBounds(value,0,value.length,textBounds)
            c.drawText(value,at.x-(textBounds.left+textBounds.right)/2f,at.y-(textBounds.top+textBounds.bottom)/2f,q)
        }
    }
    private fun coolantLabel(s:ClusterPanelState):String = s.coolant?.let {"${fmt(it)} °C"}?:when(s.coolantCondition) {
        ClusterCoolantCondition.COLD->if(labelsForNativeEnglish)"Cold"else"Холодный"
        ClusterCoolantCondition.NORMAL->""
        ClusterCoolantCondition.HOT->if(labelsForNativeEnglish)"Overheat"else"Перегрев"
        ClusterCoolantCondition.UNKNOWN->"—"
    }
    private fun duration(ms:Long?)=ms?.let {"${it/3600000}:${(it/60000%60).toString().padStart(2,'0')}"}?:"—"
    private fun distance(nav:NavigationState,l:ClusterLabels)=if(nav.distanceToManeuver>=1000)"${fmt(nav.distanceToManeuver/1000.0,1)} ${l.km}"else"${nav.distanceToManeuver} ${l.metres}"
    private fun arrow(c:Canvas,x:Float,y:Float,size:Float,nav:NavigationState?,relief:Boolean=sportDesign){
        if(nav==null)return
        c.save();c.translate(x,y);c.scale(size/100,size/100)
        if(nav.roundaboutExit!=null){c.drawCircle(50f,48f,31f,ink(blue,9f));text(c,"${nav.roundaboutExit}",50f,61f,36f)}
        else {
            val icon=when(nav.maneuver) {
                Maneuver.LEFT->ClusterOemIcon.NAV_LEFT
                Maneuver.RIGHT->ClusterOemIcon.NAV_RIGHT
                Maneuver.STRAIGHT->ClusterOemIcon.NAV_STRAIGHT
            }
            if(relief)oem.drawSculptedNavigation(c,icon,blue,dark)else oem.draw(c,icon,0f,0f,100f,neutralTint=blue)
        };c.restore()
    }
    private fun sideScale(c:Canvas,fraction:Double?,temperature:Boolean){
        val segments=verticalSegments.getOrPut(temperature) {
            ClusterSegments.build(12,25f,4f){t->ClusterSegments.sidePoint(1-t,temperature)}
        }
        val active=fraction?.let {ceil(it.coerceIn(0.0,1.0)*segments.size).toInt()}?:0
        segments.forEachIndexed {index,shape->
            val critical=if(temperature)index>=segments.size-2 else index==0
            c.drawPath(shape,ink(when {critical->red;!temperature&&index<active->blue
                else->if(dark)Color.rgb(25,46,60)else Color.rgb(172,194,209)}))
        }
        if(temperature) {
            val progress=fraction?.toFloat()?.coerceIn(0f,1f)?:0f
            val partial=thermalSideFill?.takeIf {it.first==progress}?.second?:
                ClusterSegments.build(12,25f,4f,progress){t->ClusterSegments.sidePoint(1-t,true)}
                    .also {thermalSideFill=progress to it}
            partial.forEachIndexed {i,path->c.drawPath(path,ink(if(i>=10)red else blue))}
        }
    }
    private fun header(c:Canvas,s:ClusterPanelState,clock:String,beltVisible:Boolean,design:ClusterDesign){
        val native=s.indicators?:ClusterJ7Indicators.fallback(s.lamps)
        ClusterIndicatorLayout.place(ClusterJ7Indicators.presentation(native),false,design).filter {it.header&&(beltVisible||it.indicator.bit!=107)}.forEach {slot->
            oem.drawNative(c,slot.indicator,slot.x,slot.y,slot.size,labelsForNativeEnglish,white,centerVisible=true)
        }
        if(ClusterLamp.LEFT in s.lamps)oem.draw(c,ClusterOemIcon.LEFT,12f,ClusterHeaderLayout.ICON_TOP,ClusterHeaderLayout.ICON_SIZE,centerVisible=true)
        if(ClusterLamp.RIGHT in s.lamps)oem.draw(c,ClusterOemIcon.RIGHT,1974f,ClusterHeaderLayout.ICON_TOP,ClusterHeaderLayout.ICON_SIZE,centerVisible=true)
    }
    fun islandTarget(notice:ClusterIslandNotice?)=island.target(notice)
    fun drawWarningOverlay(c:Canvas,s:ClusterPanelState,design:ClusterDesign,dark:Boolean,l:ClusterLabels) {
        c.save();c.scale(1920f/2048f,720f/768f)
        warningStrip.drawIndicators(c,s,if(dark)Color.rgb(245,250,255)else Color.rgb(13,29,42),l,design,backdrop=true,dark=dark)
        c.restore()
    }
    fun drawIsland(c:Canvas,s:ClusterPanelState,dark:Boolean,clock:String,l:ClusterLabels,accent:ClusterAccent,
                   width:Float,height:Float,progress:Float,notice:ClusterIslandNotice?) {
        c.save();c.scale(1920f/2048f,720f/768f)
        island.draw(c,s,clock,dark,if(dark)accent.darkArgb else accent.lightArgb,l,width,height,progress,notice,readouts=false)
        c.restore()
    }
    private fun footer(c:Canvas,s:ClusterPanelState,l:ClusterLabels){

        // Centre visible glyphs between the separator and the bottom fascia edge.
        text(c,"ODO  ${fmt(s.odometer)} ${l.km}",246f,0f,26f,muted,maxWidth=400f,centerY=(700f+757f)/2)
        run {
            s.driveMode?.let {mode->
                val tint=if(dark)mode.accent.darkArgb else mode.accent.lightArgb
                val title=if(labelsForNativeEnglish)mode.englishTitle else mode.title
                text(c,title,1024f,737f,33f,tint,maxWidth=302f)
            }
        }
        s.limiter?.let {lim->
            oem.draw(c,ClusterOemIcon.LIMITER,1660f,702f,42f,neutralTint=white)
            text(c,"LIM  ${lim.targetKmh?.toString()?:"—"}",1830f,736f,32f,white,maxWidth=250f)
        }
    }
    private fun fuelIcon(c:Canvas,x:Float,y:Float,scale:Float,reserve:Boolean=false,neutralTint:Int=white) {
        oem.draw(c,if(reserve)ClusterOemIcon.FUEL_LOW else ClusterOemIcon.FUEL_NORMAL,
            x-8*scale,y-10*scale,56*scale,neutralTint=neutralTint)
    }
    private fun thermometer(c:Canvas,x:Float,y:Float,scale:Float,condition:ClusterCoolantCondition) {
        oem.draw(c,ClusterOemIcon.coolant(condition),x-10*scale,y-12*scale,64*scale,neutralTint=white)
    }
    private fun clockIcon(c:Canvas,x:Float,y:Float,scale:Float,tint:Int=white) {
        oem.draw(c,ClusterOemIcon.CLOCK,x,y,36*scale,neutralTint=tint)
    }
    private fun roadIcon(c:Canvas,x:Float,y:Float,scale:Float,tint:Int=white) {
        oem.draw(c,ClusterOemIcon.ROAD,x,y,36*scale,neutralTint=tint)
    }
}

internal data class ClusterLabels(val kmh:String,val rpm:String,val km:String,val metres:String,val liters:String,
    val consumption:String,val sinceStart:String,val noRoute:String,val stock:String="Штатная приборная панель автомобиля",val hour:String="ч",
    val warnings:Map<ClusterLamp,String> = mapOf(ClusterLamp.BRAKE to "Тормозная система",ClusterLamp.OIL_PRESSURE to "Низкое давление масла",
        ClusterLamp.COOLANT_HOT to "Перегрев двигателя",ClusterLamp.DOOR to "Открыта дверь",ClusterLamp.RESERVE to "Мало топлива"))
