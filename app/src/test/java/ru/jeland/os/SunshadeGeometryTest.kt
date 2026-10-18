package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.Shade
import ru.jeland.os.ui.shadeOpenFraction
import ru.jeland.os.ui.shadeFabricRange

class SunshadeGeometryTest {
    @Test fun opensFromLeftToRightAndParksFabricAtRight() {
        assertEquals(0f,shadeOpenFraction(Shade.CLOSED)!!,0f)
        assertEquals(.5f,shadeOpenFraction(Shade.HALF)!!,0f)
        assertEquals(1f,shadeOpenFraction(Shade.OPEN)!!,0f)
        assertNull(shadeOpenFraction(null))
        for(i in 0..100) {
            val progress=i/100f;val fabric=shadeFabricRange(progress)
            assertEquals(progress,fabric.start,0f);assertEquals(1f,fabric.endInclusive,0f)
            assertEquals(1f-progress,fabric.endInclusive-fabric.start,.00001f)
        }
        assertEquals(0f,shadeFabricRange(-1f).start,0f)
        assertEquals(1f,shadeFabricRange(2f).start,0f)
    }
}
