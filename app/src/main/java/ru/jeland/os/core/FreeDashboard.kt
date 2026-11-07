package ru.jeland.os.core

import kotlin.math.abs

enum class DashboardWidgetSize(val title:String) { COMPACT("Компактный"),MEDIUM("Обычный"),LARGE("Большой") }
data class WidgetFootprint(val columns:Int,val rows:Int)
enum class WidgetCorner(val left:Boolean,val top:Boolean) {
    TOP_LEFT(true,true),TOP_RIGHT(false,true),BOTTOM_LEFT(true,false),BOTTOM_RIGHT(false,false)
}
enum class DashboardWidgetKind(val title:String) {
    CLOCK("Часы и дата"),WEATHER("Погода"),MUSIC("Музыка"),NAVIGATION("Навигация"),
    VEHICLE("Состояние автомобиля"),FUEL("Топливо"),BATTERY("Аккумулятор"),
    ACTIONS("Быстрые действия"),FAVORITES("Избранные приложения"),HERO("Баннер с автомобилем");

    fun footprint(size:DashboardWidgetSize):WidgetFootprint {
        val variants=when(this) {
            CLOCK->listOf(3 to 2,4 to 2,6 to 3)
            WEATHER->listOf(2 to 2,3 to 2,4 to 3)
            MUSIC->listOf(4 to 4,5 to 4,7 to 6)
            NAVIGATION->listOf(4 to 4,7 to 5,12 to 7)
            VEHICLE->listOf(6 to 4,6 to 9,8 to 11)
            FUEL,BATTERY->listOf(3 to 2,3 to 4,4 to 5)
            ACTIONS->listOf(12 to 2,12 to 3,12 to 4)
            FAVORITES->listOf(8 to 3,12 to 3,12 to 4)
            HERO->listOf(8 to 5,12 to 5,12 to 7)
        }
        return variants[size.ordinal].let {(w,h)->WidgetFootprint(w,h)}
    }
    val minimum:WidgetFootprint get()=when(this) {
        CLOCK->WidgetFootprint(3,2)
        WEATHER,FUEL,BATTERY->WidgetFootprint(2,2)
        MUSIC->WidgetFootprint(4,4)
        NAVIGATION,VEHICLE->WidgetFootprint(3,3)
        ACTIONS->WidgetFootprint(4,2)
        FAVORITES->WidgetFootprint(4,3)
        HERO->WidgetFootprint(5,4)
    }
}

data class DashboardWidget(val kind:DashboardWidgetKind,val x:Int,val y:Int,val size:DashboardWidgetSize=DashboardWidgetSize.MEDIUM,
    val columns:Int=0,val rows:Int=0) {
    val footprint get()=if(columns==0&&rows==0)kind.footprint(size)else WidgetFootprint(columns,rows)
    val right get()=x+footprint.columns
    val bottom get()=y+footprint.rows
    fun overlaps(other:DashboardWidget)=x<other.right&&right>other.x&&y<other.bottom&&bottom>other.y
    /** The opposite corner stays fixed; clamp before arithmetic so corrupt deltas cannot overflow. */
    fun resized(corner:WidgetCorner,dx:Int,dy:Int):DashboardWidget {
        val min=kind.minimum
        val left=if(corner.left)(x+dx.coerceIn(-FreeDashboardLayout.COLUMNS,FreeDashboardLayout.COLUMNS)).coerceIn(0,right-min.columns)else x
        val top=if(corner.top)(y+dy.coerceIn(-FreeDashboardLayout.ROWS,FreeDashboardLayout.ROWS)).coerceIn(0,bottom-min.rows)else y
        val right=if(corner.left)right else (right+dx.coerceIn(-FreeDashboardLayout.COLUMNS,FreeDashboardLayout.COLUMNS)).coerceIn(x+min.columns,FreeDashboardLayout.COLUMNS)
        val bottom=if(corner.top)bottom else (bottom+dy.coerceIn(-FreeDashboardLayout.ROWS,FreeDashboardLayout.ROWS)).coerceIn(y+min.rows,FreeDashboardLayout.ROWS)
        return copy(x=left,y=top,columns=right-left,rows=bottom-top)
    }
}

/** Integer cells are resolution independent. Only one instance of each existing widget is allowed
 * (in particular, the native map has a single rendering surface). */
data class FreeDashboardLayout(val widgets:List<DashboardWidget>) {
    val bottom get()=widgets.maxOfOrNull {it.bottom}?:0
    fun accepts(widget:DashboardWidget):Boolean {
        val f=widget.footprint;val min=widget.kind.minimum
        return f.columns in min.columns..COLUMNS&&f.rows in min.rows..ROWS&&
            widget.x in 0..COLUMNS-f.columns&&widget.y in 0..ROWS-f.rows
    }
    fun place(widget:DashboardWidget):FreeDashboardLayout?=if(accepts(widget)&&widgets.any {it.kind==widget.kind})
        copy(widgets=widgets.map {if(it.kind==widget.kind)widget else it})else null
    fun raise(kind:DashboardWidgetKind)=widgets.find {it.kind==kind}?.let {copy(widgets=widgets.filterNot {it.kind==kind}+it)}?:this
    fun lower(kind:DashboardWidgetKind)=widgets.find {it.kind==kind}?.let {copy(widgets=listOf(it)+widgets.filterNot {it.kind==kind})}?:this
    fun move(kind:DashboardWidgetKind,x:Int,y:Int):FreeDashboardLayout? {
        val old=widgets.find {it.kind==kind}?:return null
        val moved=old.copy(x=x.coerceIn(0,COLUMNS-old.footprint.columns),y=y.coerceIn(0,ROWS-old.footprint.rows))
        return place(moved)
    }
    fun remove(kind:DashboardWidgetKind)=copy(widgets=widgets.filterNot {it.kind==kind})
    fun resize(kind:DashboardWidgetKind,size:DashboardWidgetSize):FreeDashboardLayout? {
        val old=widgets.find {it.kind==kind}?:return null
        val f=kind.footprint(size)
        return place(old.copy(size=size,columns=0,rows=0,x=old.x.coerceAtMost(COLUMNS-f.columns),y=old.y.coerceAtMost(ROWS-f.rows)))
    }
    fun square(kind:DashboardWidgetKind):FreeDashboardLayout? {
        val old=widgets.find {it.kind==kind}?:return null
        val side=maxOf(6,kind.minimum.columns,kind.minimum.rows)
        return place(old.copy(columns=side,rows=side,x=old.x.coerceAtMost(COLUMNS-side),y=old.y.coerceAtMost(ROWS-side)))
    }
    fun add(kind:DashboardWidgetKind):FreeDashboardLayout? {
        if(widgets.any {it.kind==kind})return null
        val placed=vacancy(DashboardWidget(kind,0,0))?:return null
        return copy(widgets=widgets+placed)
    }
    private fun vacancy(candidate:DashboardWidget):DashboardWidget? {
        // Prefer empty space for a new widget, while editing/moving explicitly permits overlaps.
        if(accepts(candidate)&&widgets.none {it.overlaps(candidate)})return candidate
        return (0..ROWS-candidate.footprint.rows).flatMap {y->(0..COLUMNS-candidate.footprint.columns).map {x->candidate.copy(x=x,y=y)}}
            .filter {accepts(it)&&widgets.none {other->other.overlaps(it)}}
            .minWithOrNull(compareBy<DashboardWidget> {abs(it.x-candidate.x)+abs(it.y-candidate.y)}.thenBy {it.y}.thenBy {it.x})?:candidate.takeIf(::accepts)
    }
    fun encode()="2;"+widgets.joinToString(";"){"${it.kind.name},${it.x},${it.y},${it.size.name},${it.columns},${it.rows}"}
    companion object {
        const val COLUMNS=12
        const val ROWS=48
        fun default()=FreeDashboardLayout(listOf(
            DashboardWidget(DashboardWidgetKind.CLOCK,0,0),DashboardWidget(DashboardWidgetKind.WEATHER,4,0),
            DashboardWidget(DashboardWidgetKind.MUSIC,7,0),DashboardWidget(DashboardWidgetKind.NAVIGATION,0,2),
            DashboardWidget(DashboardWidgetKind.VEHICLE,3,7),DashboardWidget(DashboardWidgetKind.FUEL,0,9),
            DashboardWidget(DashboardWidgetKind.BATTERY,9,9),
            DashboardWidget(DashboardWidgetKind.ACTIONS,0,16,DashboardWidgetSize.COMPACT),
            DashboardWidget(DashboardWidgetKind.FAVORITES,0,18)))

        fun decode(value:String):FreeDashboardLayout {
            val version=value.substringBefore(';').toIntOrNull()
            if((version!=1&&version!=2)||!value.contains(';'))return default()
            val entries=value.substring(2).split(';').filter {it.isNotBlank()}
            if(entries.isEmpty())return FreeDashboardLayout(emptyList())
            var layout=FreeDashboardLayout(emptyList())
            entries.take(DashboardWidgetKind.entries.size*2).forEach {encoded->
                val widget=runCatching {
                    val fields=encoded.split(',');require(fields.size==if(version==1)4 else 6)
                    val width=if(version==1)0 else fields[4].toInt()
                    val height=if(version==1)0 else fields[5].toInt()
                    require((width==0&&height==0)||(width>0&&height>0))
                    DashboardWidget(DashboardWidgetKind.valueOf(fields[0]),fields[1].toInt(),fields[2].toInt(),DashboardWidgetSize.valueOf(fields[3]),width,height)
                }.getOrNull()?:return@forEach
                if(layout.widgets.none {it.kind==widget.kind}&&layout.accepts(widget))layout=layout.copy(widgets=layout.widgets+widget)
            }
            return layout.takeIf {it.widgets.isNotEmpty()}?:default()
        }
    }
}
