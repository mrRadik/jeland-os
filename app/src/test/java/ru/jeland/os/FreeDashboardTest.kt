package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentInterfaceRepository

class FreeDashboardTest {
    private fun valid(layout:FreeDashboardLayout) {
        assertEquals(layout.widgets.size,layout.widgets.map {it.kind}.distinct().size)
        layout.widgets.forEach {assertTrue("Invalid placement: $it",layout.accepts(it))}
    }
    @Test fun defaultLayoutHasNoOverlapsAndEveryWidgetFitsTheGrid() {
        val layout=FreeDashboardLayout.default();valid(layout)
        assertEquals(layout,FreeDashboardLayout.decode(layout.encode()))
        assertTrue(layout.widgets.any {it.kind==DashboardWidgetKind.NAVIGATION})
    }
    @Test fun overlappingDropIsAllowedAndOutOfBoundsDropsAreClamped() {
        val a=DashboardWidget(DashboardWidgetKind.CLOCK,0,0)
        val b=DashboardWidget(DashboardWidgetKind.WEATHER,4,0)
        val layout=FreeDashboardLayout(listOf(a,b))
        val overlapping=layout.move(a.kind,3,0)!!
        assertTrue(overlapping.widgets.first().overlaps(b))
        assertEquals(overlapping,FreeDashboardLayout.decode(overlapping.encode()))
        assertEquals(a,layout.widgets.first())
        val moved=layout.move(a.kind,-50,500)!!
        assertEquals(0,moved.widgets.first().x)
        assertEquals(FreeDashboardLayout.ROWS-a.footprint.rows,moved.widgets.first().y)
        valid(moved)
    }
    @Test fun presetResizeKeepsPositionAndDoesNotMoveOtherWidgets() {
        val layout=FreeDashboardLayout.default()
        val changed=layout.resize(DashboardWidgetKind.MUSIC,DashboardWidgetSize.LARGE)!!
        valid(changed)
        assertEquals(DashboardWidgetSize.LARGE,changed.widgets.first {it.kind==DashboardWidgetKind.MUSIC}.size)
        assertEquals(layout.widgets.filterNot {it.kind==DashboardWidgetKind.MUSIC},changed.widgets.filterNot {it.kind==DashboardWidgetKind.MUSIC})
    }
    @Test fun allExistingKindsCanBeAddedOnceAndRemoved() {
        var layout=FreeDashboardLayout(emptyList())
        DashboardWidgetKind.entries.forEach {kind->layout=layout.add(kind)!!;valid(layout);assertNull(layout.add(kind))}
        DashboardWidgetKind.entries.forEach {layout=layout.remove(it)}
        assertTrue(layout.widgets.isEmpty())
        assertEquals(layout,FreeDashboardLayout.decode(layout.encode()))
    }
    @Test fun damagedOrFutureSettingsCannotProduceInvisibleOrOverflowedTiles() {
        assertEquals(FreeDashboardLayout.default(),FreeDashboardLayout.decode("3;future format"))
        assertEquals(FreeDashboardLayout.default(),FreeDashboardLayout.decode("1;CLOCK,2147483647,0,MEDIUM"))
        val decoded=FreeDashboardLayout.decode("1;CLOCK,0,0,MEDIUM;CLOCK,8,8,LARGE;WEATHER,0,0,MEDIUM;FUTURE,0,0,MEDIUM;MUSIC,7,0,MEDIUM")
        assertEquals(listOf(DashboardWidgetKind.CLOCK,DashboardWidgetKind.WEATHER,DashboardWidgetKind.MUSIC),decoded.widgets.map {it.kind})
        valid(decoded)
    }
    @Test fun everyCornerKeepsTheOppositeCornerFixedAndClampsAtMinimumAndCanvasEdges() {
        val initial=DashboardWidget(DashboardWidgetKind.CLOCK,3,5,columns=6,rows=6)
        WidgetCorner.entries.forEach {corner->
            val changed=initial.resized(corner,if(corner.left)1 else -1,if(corner.top)1 else -1)
            assertEquals(5,changed.footprint.columns);assertEquals(5,changed.footprint.rows)
            assertEquals(if(corner.left)initial.right else initial.x,if(corner.left)changed.right else changed.x)
            assertEquals(if(corner.top)initial.bottom else initial.y,if(corner.top)changed.bottom else changed.y)
            val smallest=initial.resized(corner,if(corner.left)Int.MAX_VALUE else Int.MIN_VALUE,if(corner.top)Int.MAX_VALUE else Int.MIN_VALUE)
            assertEquals(initial.kind.minimum,smallest.footprint)
            val biggest=initial.resized(corner,if(corner.left)Int.MIN_VALUE else Int.MAX_VALUE,if(corner.top)Int.MIN_VALUE else Int.MAX_VALUE)
            assertTrue(FreeDashboardLayout(listOf(biggest)).accepts(biggest))
            if(corner.left)assertEquals(0,biggest.x)else assertEquals(12,biggest.right)
            if(corner.top)assertEquals(0,biggest.y)else assertEquals(48,biggest.bottom)
        }
    }
    @Test fun arbitrarySizesSquaresAndLayerOrderSurviveSavingAndLegacyLayoutsMigrate() {
        val legacy=FreeDashboardLayout.decode("1;CLOCK,0,0,MEDIUM;MUSIC,0,0,COMPACT")
        assertEquals(2,legacy.widgets.size)
        assertEquals(legacy,FreeDashboardLayout.decode(legacy.encode()))
        val square=legacy.add(DashboardWidgetKind.ACTIONS)!!.square(DashboardWidgetKind.ACTIONS)!!
            .add(DashboardWidgetKind.FAVORITES)!!.square(DashboardWidgetKind.FAVORITES)!!
        listOf(DashboardWidgetKind.ACTIONS,DashboardWidgetKind.FAVORITES).forEach {kind->
            val f=square.widgets.first {it.kind==kind}.footprint;assertEquals(f.columns,f.rows)
        }
        val front=square.raise(DashboardWidgetKind.CLOCK)
        assertEquals(DashboardWidgetKind.CLOCK,front.widgets.last().kind)
        assertEquals(DashboardWidgetKind.CLOCK,front.lower(DashboardWidgetKind.CLOCK).widgets.first().kind)
        assertEquals(front,FreeDashboardLayout.decode(front.encode()))
        assertEquals(FreeDashboardLayout.default(),FreeDashboardLayout.decode("2;MUSIC,0,0,MEDIUM,1,1"))
    }
    @Test fun layoutSurvivesRepositoryRecreationAndUnrelatedPreferenceChanges() {
        val store=MemorySettingsStore()
        val repo=PersistentInterfaceRepository(store)
        val layout=FreeDashboardLayout(listOf(DashboardWidget(DashboardWidgetKind.MUSIC,2,3,DashboardWidgetSize.COMPACT)))
        repo.change(repo.state.value.copy(style=DashboardStyle.FREE,freeLayout=layout))
        val recreated=PersistentInterfaceRepository(store)
        assertEquals(layout,recreated.state.value.freeLayout)
        recreated.change(recreated.state.value.copy(showFuel=false,style=DashboardStyle.CLASSIC))
        assertEquals(layout,PersistentInterfaceRepository(store).state.value.freeLayout)
    }
}
