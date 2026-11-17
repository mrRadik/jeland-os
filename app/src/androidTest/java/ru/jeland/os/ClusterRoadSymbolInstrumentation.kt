package ru.jeland.os.ui.cluster

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import java.io.File

class ClusterRoadSymbolInstrumentation {
    @Test fun everySymbolRendersInsideItsBadgeInBothThemes() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(app.getExternalFilesDir(null),"qa-cluster-road-127").apply {mkdirs()}
        val labels=listOf("Камера","Контроль","Полоса","Переезд","Перекрёсток","Мобильная камера",
            "Неровность","Переход","Поворот","Тоннель","Опасность","Пост ДПС","Населённый пункт",
            "Конец населённого пункта","Остановка запрещена","Платон","Весовой контроль","Участок",
            "Стоп-линия","Порог скорости","Финиш","Другой объект")
        val symbols=ClusterRoadAlertSymbols()
        for(dark in listOf(true,false)) {
            val canvasImage=Bitmap.createBitmap(1536,1224,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(canvasImage)
            val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=if(dark)Color.rgb(241,248,253)else Color.rgb(13,29,42)
                textSize=34f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
                textAlign=Paint.Align.CENTER
            }
            canvas.drawColor(if(dark)Color.rgb(6,18,29)else Color.rgb(239,245,250))
            canvas.drawText("Значки предупреждений на приборке",768f,48f,text)
            val tint=if(dark)ClusterAccent.CYAN.darkArgb else ClusterAccent.CYAN.lightArgb
            for((index,symbol) in RoadAlertSymbol.entries.withIndex()) {
                val x=(index%4)*384f+192f;val y=(index/4)*190f+148f
                canvas.save();canvas.translate(x,y);canvas.scale(2f,2f)
                symbols.draw(canvas,symbol,-32f,0f,tint,dark);canvas.restore()
                text.textSize=24f;canvas.drawText(labels[index],x,y+100f,text)
                var visible=0
                for(py in (y-45).toInt()..(y+45).toInt())for(px in (x-45).toInt()..(x+45).toInt())
                    if(canvasImage.getPixel(px,py)==tint)visible++
                assertTrue("Missing glyph: $symbol/$dark",visible>25)
            }
            File(folder,"symbols-${if(dark)"dark"else"light"}.png").outputStream().use {canvasImage.compress(Bitmap.CompressFormat.PNG,100,it)}
            canvasImage.recycle()
        }
    }
}
