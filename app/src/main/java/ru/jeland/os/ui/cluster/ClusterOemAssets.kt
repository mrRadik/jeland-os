package ru.jeland.os.ui.cluster

import android.content.res.Resources
import android.graphics.*
import ru.jeland.os.core.ClusterCoolantCondition
import ru.jeland.os.core.ClusterIndicatorSpec

/** Original SOP6 glyphs. Warning colours are never recoloured with the design accent. */
internal enum class ClusterOemIcon(val file:String,val neutral:Boolean=false) {
    LIMITER("LED_Lim.png",true), BODY("LED_MiniCar.png",true), LEFT("LED_LeftTurn.png"), RIGHT("LED_RightTurn.png"),
    LOW_BEAM("LED_NearBeam-Green.png"), HIGH_BEAM("LED_HighBeam.png"), POSITION("LED_Position.png"),
    FRONT_FOG("LED_FrontFog-Green.png"), REAR_FOG("LED_RearFog-Yellow.png"),
    BELT_RED("LED_SeatBelt-Red.png"), BELT_GREEN("LED_SeatBelt-Green.png"), BELT_GREY("LED_SeatBelt-Grey.png"),
    EPB_RED("LED_EPB-Red.png"), EPB_YELLOW("LED_EPB-Yellow.png"),
    AUTO_HOLD_GREEN("LED_AVH-Green.png"), AUTO_HOLD_WHITE("LED_AVH-White.png"), AUTO_HOLD_YELLOW("LED_AVH-Yellow.png"),
    START_STOP_GREEN("LED_StartStop-Green.png"), START_STOP_WHITE("LED_StartStop-White.png"), START_STOP_YELLOW("LED_StartStop-Yellow.png"),
    ABS("LED_ABS-Yellow.png"), ESP("LED_ElecStability-Yellow.png"), ESP_OFF("LED_ElecStabilityOFF-Yellow.png"),
    EPS_YELLOW("LED_EPS-Yellow.png"), EPS_RED("LED_EPS-Red.png"), OIL_PRESSURE("LED_OilPressure-Red.png"),
    CHARGING("LED_Charging-Red.png"), AIRBAG("LED_AirBag-Red.png"), BRAKE_FLUID("LED_BrakeFluidLevel-Red.png"),
    TPMS("LED_TPMS-Yellow.png"), CHECK_ENGINE("LED_MILEmission-Yellow.png"), EPC("LED_EPC-Yellow.png"),
    GEARBOX_YELLOW("LED_GearBoxFault-Yellow.png"), GEARBOX_RED("LED_GearBoxFault-Red.png"),
    COOLANT_COLD("LED_CoolantTemp-Blue.png"), COOLANT_NORMAL("LED_CoolantTemp-Normal.png",true), COOLANT_HOT("LED_CoolantTemp-Red.png"),
    FUEL_NORMAL("LED_FuelLevel-Normal.png",true), FUEL_LOW("LED_FuelLevelLow-Yellow.png"), ICE("LED_IceRoad-White.png"),
    BSD_GREEN("LED_BSD-Green.png"), BSD_YELLOW("LED_BSD-Yellow.png"),
    LANE_GREEN("LED_LaneKeeping-Green.png"), LANE_GREY("LED_LaneKeeping-Grey.png"), LANE_RED("LED_LaneKeeping-Red.png"), LANE_YELLOW("LED_LaneKeeping-Yellow.png"),
    LDW_GREEN("LED_LDWRight-Green.png"), LDW_GREY("LED_LDWRight-Grey.png"), LDW_YELLOW("LED_LDWRight-Yellow.png"),
    WASHER("LED_GWL-Yellow.png"), SERVICE("LED_MaintenanceTip.png"), CRUISE("LED_Cruise-Green.png"),
    HDC_GREEN("LED_HDC-Green.png"), HDC_YELLOW("LED_HDC-Yellow.png"),
    CLOCK("metric/clock.png",true), ROAD("metric/road.png",true),
    NAV_LEFT("navigation/left.png",true), NAV_RIGHT("navigation/right.png",true), NAV_STRAIGHT("navigation/straight.png",true);

    val assetPath:String get()="cluster/oem/"+if('/' in file)file else "indicator/$file"
    companion object {
        fun coolant(condition:ClusterCoolantCondition)=when(condition) {
            ClusterCoolantCondition.COLD->COOLANT_COLD
            ClusterCoolantCondition.HOT->COOLANT_HOT
            ClusterCoolantCondition.NORMAL,ClusterCoolantCondition.UNKNOWN->COOLANT_NORMAL
        }
    }
}

internal class ClusterOemAssets(private val resources:Resources) {
    private val cache=mutableMapOf<String,Bitmap>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val visibleBounds=mutableMapOf<Bitmap,Rect>()
    private fun visibleOffset(source:Bitmap,scale:Float):Pair<Float,Float> {
        val box=visibleBounds.getOrPut(source) {
            var left=source.width;var top=source.height;var right=-1;var bottom=-1
            for(y in 0 until source.height)for(x in 0 until source.width)if(Color.alpha(source.getPixel(x,y))>16) {
                left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)
            }
            if(right<0)Rect(0,0,source.width,source.height)else Rect(left,top,right+1,bottom+1)
        }
        return (source.width/2f-box.exactCenterX())*scale to (source.height/2f-box.exactCenterY())*scale
    }
    fun bitmap(icon:ClusterOemIcon):Bitmap=bitmap(icon.assetPath)
    fun bitmap(path:String):Bitmap=cache.getOrPut(path) {
        resources.assets.open(path).use {stream->
            checkNotNull(BitmapFactory.decodeStream(stream,null,BitmapFactory.Options().apply {inScaled=false}))
        }
    }
    fun draw(c:Canvas,icon:ClusterOemIcon,x:Float,y:Float,width:Float,height:Float=width,neutralTint:Int?=null,centerVisible:Boolean=false) {
        val source=bitmap(icon)
        val scale=minOf(width/source.width,height/source.height)
        val w=source.width*scale;val h=source.height*scale
        val bounds=RectF(x+(width-w)/2,y+(height-h)/2,x+(width+w)/2,y+(height+h)/2)
        if(centerVisible)visibleOffset(source,scale).let {(dx,dy)->bounds.offset(dx,dy)}
        // Only neutral pictograms follow theme text colour; blue/red/yellow/green states stay OEM.
        paint.colorFilter=if(icon.neutral&&neutralTint!=null)PorterDuffColorFilter(neutralTint,PorterDuff.Mode.SRC_IN)else null
        c.drawBitmap(source,null,bounds,paint)
    }
    private var navigationRelief:Bitmap?=null
    private var navigationReliefKey=""
    /** Gradient and light bloom use the original OEM alpha mask, never a new arrow shape. */
    fun drawSculptedNavigation(c:Canvas,icon:ClusterOemIcon,tint:Int,dark:Boolean) {
        val key="${icon.name}:$tint:$dark"
        if(navigationReliefKey!=key) {
            val source=bitmap(icon)
            val image=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
            val out=Canvas(image)
            val scale=minOf(160f/source.width,160f/source.height)
            val w=source.width*scale;val h=source.height*scale
            val rect=RectF(96f-w/2,96f-h/2,96f+w/2,96f+h/2)
            val brush=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter=PorterDuffColorFilter(tint,PorterDuff.Mode.SRC_IN)
                if(dark)setShadowLayer(9f,0f,0f,tint)
            }
            out.drawBitmap(source,null,rect,brush)
            brush.clearShadowLayer();brush.colorFilter=null
            val layer=out.saveLayer(null,null)
            out.drawBitmap(source,null,rect,brush)
            brush.shader=LinearGradient(25f,20f,155f,165f,
                intArrayOf(if(dark)Color.WHITE else Color.rgb(226,248,255),tint,
                    Color.rgb(Color.red(tint)*2/3,Color.green(tint)*2/3,Color.blue(tint)*2/3)),
                floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
            brush.xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            out.drawRect(0f,0f,192f,192f,brush);out.restoreToCount(layer)
            navigationRelief=image;navigationReliefKey=key
        }
        paint.colorFilter=null
        navigationRelief?.let {c.drawBitmap(it,null,RectF(-10f,-10f,110f,110f),paint)}
    }
    fun drawNative(c:Canvas,icon:ClusterIndicatorSpec,x:Float,y:Float,size:Float,english:Boolean=false,textTint:Int=Color.WHITE,centerVisible:Boolean=false) {
        val caption=if(english)icon.captionEn else icon.captionRu
        val pictureHeight=if(caption!=null)size*.72f else size
        if(icon.asset!=null) {
            val source=bitmap(icon.asset)
            val scale=minOf(size/source.width,pictureHeight/source.height)
            val w=source.width*scale;val h=source.height*scale
            val tint=icon.fixedTint?:textTint.takeIf {icon.asset.endsWith("-White.png")||icon.key=="LED_DrivingModeNormal"}
            paint.colorFilter=tint?.let {PorterDuffColorFilter(it,PorterDuff.Mode.SRC_IN)}
            val bounds=RectF(x+(size-w)/2,y+(pictureHeight-h)/2,x+(size+w)/2,y+(pictureHeight+h)/2)
            if(centerVisible)visibleOffset(source,scale).let {(dx,dy)->bounds.offset(dx,dy)}
            c.drawBitmap(source,null,bounds,paint)
            paint.colorFilter=null
        }else {
            paint.colorFilter=null;paint.color=textTint
            paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            paint.textAlign=Paint.Align.CENTER;paint.textSize=size*.36f
            c.drawText(icon.text.orEmpty(),x+size/2,y+pictureHeight*.68f,paint)
        }
        if(caption!=null) {
            paint.color=textTint;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            paint.textAlign=Paint.Align.CENTER;paint.textSize=size*.20f
            c.drawText(caption,x+size/2,y+size,paint)
        }
    }
    fun close(){visibleBounds.clear();cache.clear();navigationRelief=null;navigationReliefKey=""}
}
