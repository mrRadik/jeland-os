package ru.jeland.os.ui.cluster

import android.graphics.*
import android.content.res.Resources
import ru.jeland.os.R
import ru.jeland.os.core.ClusterDesign
import ru.jeland.os.core.ClusterNavigationMode
import kotlin.math.cos
import kotlin.math.sin

/** Cached native housing with the owner-supplied JELAND background and theme colour grading. */
internal class SculptedClusterHousing(private val resources:Resources) {
    private var backgroundTexture:Bitmap?=null
    fun close(){backgroundTexture=null}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var dark=true
    private var accent=Color.CYAN
    private fun rgb(hex:String)=Color.parseColor(hex)
    private fun fade(c:Int,a:Int)=Color.argb(a,Color.red(c),Color.green(c),Color.blue(c))
    private fun ink(c:Int,width:Float=0f,shader:Shader?=null)=paint.apply {
        reset();isAntiAlias=true;color=c;strokeWidth=width;style=if(width==0f)Paint.Style.FILL else Paint.Style.STROKE
        strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND;this.shader=shader
    }
    private fun linear(x:Float,y:Float,x2:Float,y2:Float,vararg colors:Int)=LinearGradient(x,y,x2,y2,colors,null,Shader.TileMode.CLAMP)
    private fun radial(x:Float,y:Float,r:Float,vararg colors:Int)=RadialGradient(x,y,r,colors,null,Shader.TileMode.CLAMP)
    private fun line(c:Canvas,x:Float,y:Float,x2:Float,y2:Float,color:Int,width:Float=1f){c.drawLine(x,y,x2,y2,ink(color,width))}
    private fun oval(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,color:Int,shader:Shader?=null,width:Float=0f){c.drawOval(x-rx,y-ry,x+rx,y+ry,ink(color,width,shader))}
    private fun arc(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,start:Float,sweep:Float,color:Int,width:Float){
        c.drawArc(x-rx,y-ry,x+rx,y+ry,start,sweep,false,ink(color,width))
    }
    private fun glass(c:Canvas,path:Path,rect:RectF){
        c.save();c.translate(0f,7f);c.drawPath(path,ink(fade(Color.BLACK,if(dark)155 else 35),12f));c.restore()
        c.drawPath(path,ink(Color.WHITE,shader=linear(0f,rect.top,0f,rect.bottom,
            if(dark)rgb("#355163")else rgb("#d5e1e8"),if(dark)rgb("#040c14")else rgb("#95aab9"),
            if(dark)rgb("#182b3a")else rgb("#faffff"))))
        c.drawPath(path,ink(fade(if(dark)Color.WHITE else rgb("#516b80"),55),1f))
        c.save()
        val inner=Path(path).apply {transform(Matrix().apply {
            setScale((1f-6f/rect.width()).coerceAtLeast(.5f),(1f-6f/rect.height()).coerceAtLeast(.5f),rect.centerX(),rect.centerY())
        })}
        c.clipPath(inner)
        c.drawRect(rect,ink(Color.WHITE,shader=radial(rect.centerX(),rect.top-rect.height()*.15f,rect.height()*1.5f,
            if(dark)rgb("#102a3a")else rgb("#f1f7fb"),if(dark)rgb("#07131e")else rgb("#d5e1e9"),
            if(dark)rgb("#030a11")else rgb("#b9cad6"))))
        c.drawRect(rect.left,rect.top,rect.right,rect.top+rect.height()*.20f,ink(Color.WHITE,shader=linear(0f,rect.top,0f,rect.top+rect.height()*.20f,
            fade(Color.WHITE,if(dark)25 else 70),Color.TRANSPARENT)))
        c.restore()
        c.drawPath(path,ink(Color.WHITE,1.1f,linear(rect.left,rect.top,rect.right,rect.bottom,
            fade(if(dark)rgb("#d6efff")else Color.WHITE,145),fade(accent,12),fade(accent,55))))
    }
    private fun pocket(c:Canvas,x:Float,y:Float,rx:Float,ry:Float){
        // The top curve ends EXACTLY where the lower circular arc starts.
        // Previously arcTo inserted short straight connectors, creating visible notches.
        val angle=Math.toRadians(46.0)
        val half=rx*ClusterDialMetrics.POCKET*cos(angle).toFloat()
        val left=x-half;val right=x+half;val lower=y+ry*ClusterDialMetrics.POCKET*sin(angle).toFloat()
        val path=Path().apply {
            moveTo(left,lower);cubicTo(x-rx*.30f,y+ry*.28f,x+rx*.30f,y+ry*.28f,right,lower)
            arcTo(RectF(x-rx*ClusterDialMetrics.POCKET,y-ry*ClusterDialMetrics.POCKET,x+rx*ClusterDialMetrics.POCKET,y+ry*ClusterDialMetrics.POCKET),46f,88f);close()
        }
        glass(c,path,RectF(left,y+ry*.36f,right,y+ry*.86f))
        val lip=Path().apply {moveTo(left,lower);cubicTo(x-rx*.30f,y+ry*.28f,x+rx*.30f,y+ry*.28f,right,lower)}
        c.drawPath(lip,ink(fade(Color.BLACK,if(dark)200 else 60),6f))
        c.drawPath(lip,ink(Color.WHITE,1.6f,linear(left,lower,x,lower-ry*.21f,
            fade(accent,30),fade(if(dark)rgb("#dcf5ff")else Color.WHITE,220))))
    }
    private fun dial(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,secondary:Boolean){
        // Broad, feathered seat shadow makes the housing stand proud of the surrounding fascia.
        for(i in 10 downTo 1){
            val r=1f+i*.008f
            oval(c,x+2,y+12,rx*r,ry*r,fade(Color.BLACK,if(dark)8 else 3))
        }
        val steel=if(dark)intArrayOf(rgb("#1a2b39"),rgb("#e6f0f5"),rgb("#526a7e"),rgb("#142432"),rgb("#8299ab"),rgb("#0b1823"),rgb("#edf5f9"),rgb("#344f62"),rgb("#1a2b39"))
            else intArrayOf(rgb("#758b9d"),Color.WHITE,rgb("#b5c6d3"),rgb("#5d7486"),rgb("#e7f1f6"),rgb("#8b9fae"),Color.WHITE,rgb("#91a8b8"),rgb("#758b9d"))
        oval(c,x,y,rx*1.01f,ry*1.01f,if(dark)rgb("#03080c")else rgb("#7d95a5"))
        oval(c,x,y,rx,ry,Color.WHITE,SweepGradient(x,y,steel,null))
        oval(c,x,y,rx*.994f,ry*.994f,fade(Color.WHITE,130),width=.8f)
        oval(c,x,y,rx*.977f,ry*.977f,if(dark)rgb("#0b1722")else rgb("#7690a3"))
        oval(c,x,y,rx*.965f,ry*.965f,Color.WHITE,linear(x-rx*.7f,y-ry,x+rx*.6f,y+ry,
            if(dark)rgb("#cfdeea")else Color.WHITE,if(dark)rgb("#58758b")else rgb("#afc2cf"),
            if(dark)rgb("#09131d")else rgb("#617c91"),if(dark)rgb("#708da2")else rgb("#dae9f1")))
        // Hairline machining catches the key light; no coarse pattern beneath text.
        for(i in 0..5)oval(c,x,y,rx*(.959f-i*.0032f),ry*(.959f-i*.0032f),fade(Color.WHITE,if(i%2==0)16 else 5),width=.55f)
        oval(c,x,y,rx*.929f,ry*.929f,if(dark)rgb("#02080e")else rgb("#829aab"))
        oval(c,x,y,rx*.914f,ry*.914f,Color.WHITE,linear(x,y-ry,x,y+ry,
            if(dark)rgb("#030a11")else rgb("#b4c7d4"),if(dark)rgb("#1e3446")else rgb("#faffff"),if(dark)rgb("#03090f")else rgb("#bdcfdb")))
        oval(c,x,y,rx*.893f,ry*.893f,fade(accent,110),width=1.4f)
        oval(c,x,y,rx*ClusterDialMetrics.FACE,ry*ClusterDialMetrics.FACE,Color.WHITE,radial(x-rx*.19f,y-ry*.42f,rx*1.5f,
            if(dark)rgb("#163347")else rgb("#f4f9fc"),if(dark)rgb("#091824")else rgb("#d9e5ed"),
            if(dark)rgb("#020810")else rgb("#a4bdce")))
        // An inward-facing lip, then a convex, lit center disc: two different depth planes.
        oval(c,x,y+3,rx*.61f,ry*.61f,Color.WHITE,radial(x,y,rx*.63f,
            Color.TRANSPARENT,fade(Color.BLACK,if(dark)230 else 55)))
        oval(c,x,y,rx*ClusterDialMetrics.HUB_RIM,ry*ClusterDialMetrics.HUB_RIM,Color.WHITE,linear(x-rx*.3f,y-ry*.6f,x+rx*.3f,y+ry*.6f,
            if(dark)rgb("#869eaf")else Color.WHITE,if(dark)rgb("#152b3b")else rgb("#b5cbd9"),if(dark)rgb("#030a11")else rgb("#8aa8bd")))
        oval(c,x,y,rx*.578f,ry*.578f,if(dark)rgb("#01070d")else rgb("#7698af"))
        oval(c,x,y,rx*.562f,ry*.562f,Color.WHITE,radial(x-rx*.18f,y-ry*.28f,rx*.97f,
            if(dark)rgb("#244b63")else rgb("#ffffff"),if(dark)rgb("#091c2c")else rgb("#e7f0f5"),
            if(dark)rgb("#01080f")else rgb("#b4cad9")))
        arc(c,x,y,rx*.575f,ry*.575f,208f,100f,fade(accent,35),8f)
        arc(c,x,y,rx*.576f,ry*.576f,217f,79f,fade(if(dark)rgb("#def4ff")else Color.WHITE,185),1.2f)
        arc(c,x,y,rx*.979f,ry*.979f,208f,76f,fade(Color.WHITE,160),1.2f)
        arc(c,x,y,rx*.91f,ry*.91f,43f,83f,fade(accent,80),1f)
        if(secondary)pocket(c,x,y,rx,ry)
    }
    /** Preserve the supplied texture/logo exactly; apply colour grading only at render time. */
    private fun drawBackground(c:Canvas) {
        val source=backgroundTexture?:BitmapFactory.decodeResource(resources,R.drawable.cluster_background_jeland,
            BitmapFactory.Options().apply {inScaled=false}).also {backgroundTexture=it}
        val channels=floatArrayOf(Color.red(accent)/255f,Color.green(accent)/255f,Color.blue(accent)/255f)
        val base=if(dark)floatArrayOf(3f,11f,18f)else floatArrayOf(233f,240f,245f)
        val lightSlope=floatArrayOf(-.65f,-.60f,-.55f)
        val matrix=FloatArray(20)
        for(i in 0..2) {
            val slope=if(dark).72f+channels[i]*.10f else lightSlope[i]
            matrix[i*5]=slope*.2126f
            matrix[i*5+1]=slope*.7152f
            matrix[i*5+2]=slope*.0722f
            matrix[i*5+4]=if(dark)base[i] else base[i]*.98f+channels[i]*255f*.02f
        }
        matrix[18]=1f
        val brush=ink(Color.WHITE).apply {
            isFilterBitmap=true
            colorFilter=ColorMatrixColorFilter(matrix)
        }
        // Full-viewport coordinates preserve the image's proportions; the existing fascia clips its edge.
        c.drawBitmap(source,null,RectF(0f,0f,2048f,768f),brush)
    }
    /** The map stays visible behind the instruments; only local readability scrims are opaque. */
    private fun navigation(c:Canvas,miniature:Boolean,navigationMode:ClusterNavigationMode,hasGuidance:Boolean) {
        val base=if(dark)rgb("#07131e")else rgb("#d9e4ec")
        if(miniature||navigationMode==ClusterNavigationMode.GUIDANCE) {
            c.drawColor(base)
            if(navigationMode==ClusterNavigationMode.MAP)for(i in 0..17)line(c,-100f+i*145,-30f,100f+i*145,768f,fade(accent,20),2f)
            if(navigationMode==ClusterNavigationMode.MAP)for(i in 0..7)line(c,0f,90f+i*85,2048f,70f+i*85,fade(accent,20),2f)
        }
        val mapGuidance=navigationMode==ClusterNavigationMode.MAP&&hasGuidance
        val routeCard=RectF(76f,108f,480f,if(mapGuidance)350f else 204f)
        c.drawRoundRect(routeCard,24f,24f,ink(Color.WHITE,shader=linear(0f,routeCard.top,0f,routeCard.bottom,
                fade(if(dark)rgb("#07131e")else rgb("#edf5fa"),235),
                fade(if(dark)rgb("#07131e")else rgb("#edf5fa"),195))))
        c.drawRoundRect(routeCard,24f,24f,ink(fade(accent,65),1f))
        if(mapGuidance)line(c,102f,252f,454f,252f,fade(accent,65))
        ClusterGeometry.dials(ClusterDesign.NAVIGATION).forEach {d->dial(c,d.x,d.y,d.rx,d.ry,true)}
        c.drawRect(0f,666f,2048f,768f,ink(Color.WHITE,shader=linear(0f,666f,0f,768f,
            Color.TRANSPARENT,fade(base,235))))
        fasciaEdges(c,false)
    }
    private fun glow(c:Canvas,path:Path,strength:Float=1f) {
        for((width,opacity)in listOf(15f to 12,8f to 26,3f to 125))
            c.drawPath(path,ink(fade(accent,(opacity*strength).toInt().coerceAtMost(255)),width))
        c.drawPath(path,ink(if(dark)rgb("#cdf5ff")else Color.WHITE,.9f))
    }
    /** Symmetric chamfered top and bottom fascia, sharing one outside edge. */
    private fun fasciaEdges(c:Canvas,sport:Boolean) {
        val base=if(dark)rgb("#030b13")else rgb("#e4edf3")
        val shell=Path().apply {addRoundRect(12f,8f,2036f,757f,44f,44f,Path.Direction.CW)}
        c.save();c.clipPath(shell)
        c.drawRect(0f,0f,2048f,80f,ink(base,shader=linear(0f,0f,0f,80f,
            if(dark)rgb("#102333")else Color.WHITE,base)))
        // The lower line flows into angular shoulders; no rounded badge behind the drive mode.
        c.drawRect(0f,690f,2048f,768f,ink(base,shader=linear(0f,690f,0f,768f,Color.TRANSPARENT,base)))
        val lower=Path().apply {
            moveTo(26f,700f);lineTo(396f,700f);quadTo(416f,700f,432f,716f)
            lineTo(452f,736f);quadTo(466f,750f,494f,750f);lineTo(1554f,750f)
            quadTo(1582f,750f,1596f,736f);lineTo(1616f,716f)
            quadTo(1632f,700f,1652f,700f);lineTo(2022f,700f)
        }
        c.drawPath(lower,ink(fade(if(dark)rgb("#afccdd")else rgb("#476b80"),90),1.1f))
        if(sport) {
            for(mirror in listOf(false,true)) {
                c.save();if(mirror){c.translate(2048f,0f);c.scale(-1f,1f)}
                val bottom=Path().apply {moveTo(445f,752f);lineTo(696f,752f);quadTo(730f,752f,748f,754f)}
                glow(c,bottom,1.25f)
                c.restore()
            }
        }
        c.restore()
        c.drawPath(shell,ink(Color.WHITE,2.1f,linear(0f,8f,0f,757f,
            fade(if(dark)rgb("#b4ccdc")else Color.WHITE,185),fade(accent,40),fade(if(dark)rgb("#8ca9bd")else rgb("#5c7d92"),165))))
    }
    /** Sport uses a dark, sculpted canvas with cut accents instead of the full carbon bitmap. */
    private fun sportBackdrop(c:Canvas) {
        c.drawColor(if(dark)rgb("#02060b")else rgb("#e7eff5"))
        c.drawRect(0f,80f,2048f,706f,ink(Color.WHITE,shader=radial(1024f,390f,920f,
            fade(accent,if(dark)20 else 16),Color.TRANSPARENT)))
        for(mirror in listOf(false,true)) {
            c.save();if(mirror){c.translate(2048f,0f);c.scale(-1f,1f)}
            // A narrow woven upper shoulder; the data itself sits directly on the clear canvas.
            val wing=Path().apply {moveTo(38f,86f);lineTo(718f,86f);lineTo(674f,124f);lineTo(86f,124f);close()}
            c.save();c.clipPath(wing)
            c.drawPath(wing,ink(fade(accent,if(dark)16 else 12)))
            for(i in 0..62)line(c,i*16f,124f,i*16f+32f,86f,fade(if(dark)Color.WHITE else rgb("#3e6682"),if(dark)22 else 16),4f)
            c.restore()
            val title=Path().apply {moveTo(248f,151f);lineTo(674f,151f);lineTo(652f,194f);lineTo(223f,194f);close()}
            // Only the left title receives a blade; the right side is reserved for navigation.
            if(!mirror) {
                c.drawPath(title,ink(accent,shader=linear(230f,151f,670f,194f,fade(accent,80),Color.TRANSPARENT)))
                for(x in listOf(216f,237f)) {
                    val slash=Path().apply {moveTo(x,151f);lineTo(x+14f,151f);lineTo(x-11f,194f);lineTo(x-25f,194f);close()}
                    c.drawPath(slash,ink(accent,shader=linear(x,151f,x,194f,if(dark)rgb("#c6f5ff")else Color.WHITE,accent)))
                }
            }
            val tail=Path().apply {moveTo(372f,610f);lineTo(672f,610f);lineTo(644f,640f);lineTo(350f,640f);close()}
            c.save();c.clipPath(tail)
            for(i in 0..38)line(c,170f+i*17,640f,201f+i*17,610f,fade(accent,if(dark)50 else 36),2f)
            c.restore()
            c.restore()
        }
    }
    fun draw(c:Canvas,design:ClusterDesign,isDark:Boolean,accentColor:Int,miniature:Boolean,navigationMode:ClusterNavigationMode=ClusterNavigationMode.MAP,hasGuidance:Boolean=false){
        dark=isDark;accent=accentColor
        if(design==ClusterDesign.NAVIGATION){navigation(c,miniature,navigationMode,hasGuidance);return}
        if(design==ClusterDesign.SPORT) sportBackdrop(c) else {
            c.drawColor(if(dark)rgb("#030911")else rgb("#d9e4ec"))
            drawBackground(c)
        }
        when(design){
            ClusterDesign.CLASSIC->ClusterGeometry.dials(design).forEach {d->dial(c,d.x,d.y,d.rx,d.ry,true)}
            ClusterDesign.SPORT->{
                val central=ClusterGeometry.dials(design).single()
                // A concentrated halo follows the metal ring, rather than lighting a rectangular card.
                oval(c,central.x,central.y,central.rx*1.10f,central.ry*1.10f,Color.WHITE,
                    radial(central.x,central.y,central.rx*1.10f,Color.TRANSPARENT,fade(accent,10),Color.TRANSPARENT))
                dial(c,central.x,central.y,central.rx,central.ry,true)
                for((start,sweep)in listOf(192f to 108f,22f to 115f)) {
                    arc(c,central.x,central.y,central.rx*.914f,central.ry*.914f,start,sweep,fade(accent,30),11f)
                    arc(c,central.x,central.y,central.rx*.914f,central.ry*.914f,start,sweep,accent,2.4f)
                }
                for(right in listOf(false,true)){
                    val track=Path().apply {for(i in 0..80){val p=ClusterSegments.sidePoint(i/80f,right);if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)}}
                    c.drawPath(track,ink(fade(Color.BLACK,if(dark)180 else 45),21f))
                    c.drawPath(track,ink(fade(accent,42),14f))
                    c.drawPath(track,ink(fade(Color.WHITE,85),1f))
                }
            }
            else->Unit
        }
        fasciaEdges(c,design==ClusterDesign.SPORT)
    }
}
