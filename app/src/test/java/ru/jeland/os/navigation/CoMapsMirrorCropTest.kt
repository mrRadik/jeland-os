package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.comaps.*

class CoMapsMirrorCropTest {
    @Test fun portraitSourceKeepsLowerVehicleAreaAndExcludesOverlayBands() {
        val crop=coMapsMirrorCrop(1000,1000,560,330,CoMapsViewport(200,1000,850,65))!!
        assertEquals(1000,crop.width)
        assertEquals(850,crop.top+crop.height)
        assertTrue(crop.top>=200)
        assertTrue("The vehicle above the bottom guidance offset stays visible",785 in crop.top until crop.top+crop.height)
    }
    @Test fun wideSourceCropsSidesWithoutStretchingOrLosingVerticalArea() {
        val crop=coMapsMirrorCrop(1920,720,490,294,CoMapsViewport(80,1920,650,50))!!
        assertTrue(crop.left>0)
        assertEquals(80,crop.top)
        assertEquals(570,crop.height)
        assertEquals(490.0/294,crop.width.toDouble()/crop.height,.01)
    }
    @Test fun transientZeroSizesAreSkippedAndStaleViewportStaysInsideFrame() {
        assertNull(coMapsMirrorCrop(0,720,560,330,null))
        assertNull(coMapsMirrorCrop(1920,720,0,330,null))
        val crop=coMapsMirrorCrop(480,240,560,330,CoMapsViewport(500,1920,1000,50))!!
        assertTrue(crop.left>=0&&crop.top>=0)
        assertTrue(crop.left+crop.width<=480&&crop.top+crop.height<=240)
        assertTrue(crop.width>0&&crop.height>0)
    }
}
