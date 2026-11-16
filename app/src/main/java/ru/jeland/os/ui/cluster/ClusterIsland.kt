package ru.jeland.os.ui.cluster

import android.graphics.*
import androidx.core.graphics.ColorUtils
import ru.jeland.os.core.*
import java.util.Locale

internal data class ClusterIslandNotice(val text:String,val critical:Boolean,val door:Boolean,val detail:String?=null,val road:Boolean=false,val symbol:RoadAlertSymbol=RoadAlertSymbol.OTHER)
internal fun clusterIslandNotice(state:ClusterPanelState,labels:ClusterLabels,roadAlert:RoadAlert?=null):ClusterIslandNotice? {
    val english=labels.km=="km"
    val priority=listOf(ClusterLamp.BRAKE,ClusterLamp.OIL_PRESSURE,ClusterLamp.COOLANT_HOT,ClusterLamp.DOOR,ClusterLamp.RESERVE).firstOrNull {it in state.lamps}
    val door=when(state.openings.singleOrNull()) {
        VehicleOpening.FRONT_LEFT->if(english)"Front left door open"else"Открыта передняя левая дверь"
        VehicleOpening.FRONT_RIGHT->if(english)"Front right door open"else"Открыта передняя правая дверь"
        VehicleOpening.REAR_LEFT->if(english)"Rear left door open"else"Открыта задняя левая дверь"
        VehicleOpening.REAR_RIGHT->if(english)"Rear right door open"else"Открыта задняя правая дверь"
        VehicleOpening.HOOD->if(english)"Hood open"else"Открыт капот"
        VehicleOpening.TRUNK->if(english)"Tailgate open"else"Открыт багажник"
        else->null
    }
    val text=state.message?.let {if(english)it.english else it.russian}
        ?:(if(priority==ClusterLamp.DOOR)door else null)?:labels.warnings[priority]
    if(text.isNullOrBlank())return roadAlert?.let {
        ClusterIslandNotice(it.title,false,false,it.detail.takeIf(String::isNotBlank),road=true,symbol=RoadAlertSymbol.fromTitle(it.title))
    }
    return ClusterIslandNotice(text,state.message?.critical?:(priority in listOf(ClusterLamp.BRAKE,ClusterLamp.OIL_PRESSURE,ClusterLamp.COOLANT_HOT)),priority==ClusterLamp.DOOR)
}

/** One capsule for readouts and warnings. Its changing bounds never participate in instrument layout. */
internal class ClusterIslandPainter(private val assets:ClusterOemAssets) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds=Rect()
    private val roadSymbols=ClusterRoadAlertSymbols()
    private val regular=Typeface.create("sans-serif",Typeface.NORMAL)
    private val medium=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private fun lines(notice:ClusterIslandNotice):List<String> {
        if(notice.road)return listOfNotNull(notice.text,notice.detail)

        paint.typeface=medium;paint.textSize=30f
        val limit=if(notice.door)700f else 756f
        val lines=mutableListOf("")
        for(word in notice.text.split(Regex("\\s+")).filter {it.isNotEmpty()}) {
            val next=(lines.last()+" "+word).trim()
            if(paint.measureText(next)>limit&&lines.last().isNotEmpty())lines.add(word)else lines[lines.lastIndex]=next
        }
        val out=lines.take(4).toMutableList()
        if(lines.size>4)out[3]=out[3].trimEnd()+"…"
        return out
    }
    fun target(notice:ClusterIslandNotice?):Pair<Float,Float> {
        if(notice==null)return 640f to 68f
        val lines=lines(notice)
        if(notice.road)return 880f to (96f+lines.size*34f)
        val width=(lines.maxOf {paint.measureText(it)}+64f+if(notice.door)56f else 0f).coerceIn(640f,880f)
        return width to (68f+12f+lines.size*34f+16f)
    }
    private fun centered(c:Canvas,value:String,x:Float,y:Float,size:Float,tint:Int) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.typeface=regular;paint.textSize=size;paint.color=tint
        paint.textAlign=Paint.Align.CENTER;paint.getTextBounds(value,0,value.length,bounds)
        c.drawText(value,x,y-(bounds.top+bounds.bottom)/2f,paint)
    }
    fun draw(c:Canvas,state:ClusterPanelState,clock:String,dark:Boolean,accent:Int,labels:ClusterLabels,
             width:Float,height:Float,progress:Float,notice:ClusterIslandNotice?,readouts:Boolean) {
        val p=progress.coerceIn(0f,1f);val rect=RectF(1024f-width/2,10f,1024f+width/2,10f+height)
        val radius=34f-12f*p
        val shape=Path().apply {addRoundRect(rect,radius,radius,Path.Direction.CW)}
        paint.reset();paint.isAntiAlias=true
        paint.shader=LinearGradient(0f,rect.top,0f,rect.bottom,
            if(dark)Color.rgb(20,39,54)else Color.rgb(253,255,255),if(dark)Color.rgb(4,14,23)else Color.rgb(230,240,247),Shader.TileMode.CLAMP)
        c.drawPath(shape,paint);paint.shader=null
        val text=if(dark)Color.rgb(245,250,255)else Color.rgb(13,29,42)
        val neutral=ColorUtils.setAlphaComponent(accent,if(dark)110 else 90)
        val warning=if(notice?.road==true)accent else if(notice?.critical==true)Color.rgb(232,67,77)else Color.rgb(217,155,66)
        paint.color=ColorUtils.blendARGB(neutral,warning,p);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.4f
        c.drawPath(shape,paint);paint.style=Paint.Style.FILL
        c.save();c.clipPath(shape)
        paint.color=ColorUtils.setAlphaComponent(accent,90);paint.strokeWidth=1f
        for(x in listOf(925f,1123f))c.drawLine(x,28f,x,60f,paint)
        if(readouts) {
            centered(c,clock,818f,44f,37f,text);centered(c,state.gear?:"—",1024f,44f,52f,text)
            val outside=state.outside?.let {String.format(Locale.getDefault(),if(it%1.0==0.0)"%.0f °C"else"%.1f °C",it)}?:"— °C"
            centered(c,outside,1230f,44f,33f,text)
        }
        if(notice!=null&&p>.001f) {
            val lines=lines(notice);val opacity=(255*p).toInt()
            paint.color=ColorUtils.setAlphaComponent(warning,(65*p).toInt());c.drawLine(rect.left+24f,80f,rect.right-24f,80f,paint)
            val centreY=90f+lines.size*17f
            if(notice.door) {
                c.saveLayerAlpha(null,opacity)
                assets.draw(c,ClusterOemIcon.BODY,rect.left+20f,centreY-20f,40f,neutralTint=warning,centerVisible=true)
                c.restore()
            }
            if(notice.road) {
                c.saveLayerAlpha(null,opacity)
                roadSymbols.draw(c,notice.symbol,rect.left+20f,centreY,warning,dark)
                c.restore()
            }
            paint.typeface=medium;paint.textAlign=Paint.Align.CENTER;paint.textSize=30f;paint.color=ColorUtils.setAlphaComponent(text,opacity)
            lines.forEachIndexed {i,line->
                var visible=line
                paint.textSize=if(notice.road&&i==0)32f else if(notice.road)26f else 30f
                val max=(width-64f-if(notice.road)96f else if(notice.door)56f else 0f).coerceAtLeast(1f)
                if(paint.measureText(visible)>max){while(visible.isNotEmpty()&&paint.measureText(visible+"…")>max)visible=visible.dropLast(1);visible+="…"}
                paint.getTextBounds(visible,0,visible.length,bounds)
                c.drawText(visible,1024f+if(notice.door||notice.road)24f else 0f,107f+i*34f-(bounds.top+bounds.bottom)/2f,paint)
            }
        }
        c.restore()
    }
}
