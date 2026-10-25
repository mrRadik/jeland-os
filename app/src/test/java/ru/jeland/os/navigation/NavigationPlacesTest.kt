package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.MemorySettingsStore
import ru.jeland.os.core.DurableSettingsStore

class NavigationPlacesTest {
    private val home=NavigationPlace("Мира, 1",GeoPoint(55.16,61.4),"address","Челябинск","улица Мира, 1")
    @Test fun historyAndFavoritesSurviveRepositoryRecreationWithCoordinatesAndAddress() {
        val store=MemorySettingsStore();val first=NavigationPlacesRepository(store){1234}
        assertTrue(first.rememberSelection("Мир 1",home));assertTrue(first.saveFavorite(home,"Дом"))
        val restored=NavigationPlacesRepository(store)
        assertEquals(first.history.value,restored.history.value);assertEquals(first.favorites.value,restored.favorites.value)
        assertEquals(home,restored.history.value.single().place)
        assertEquals(home.point,restored.favorites.value.single().destination.point)
        assertTrue(restored.favorites.value.single().destination.fullName.contains("Дом"))
        assertTrue(restored.favorites.value.single().destination.fullName.contains("улица Мира, 1"))
    }
    @Test fun explicitQueriesAreNormalizedDeduplicatedAndMovedToFront() {
        val repo=NavigationPlacesRepository(MemorySettingsStore())
        assertTrue(repo.rememberSearch("  Мира   1 "));repo.rememberSearch("Ленина 2");repo.rememberSearch("МИРА 1")
        assertEquals(listOf("МИРА 1","Ленина 2"),repo.history.value.map {it.query})
        assertFalse(repo.rememberSearch(" "));assertFalse(repo.rememberSearch("А"))
        assertEquals(2,repo.history.value.size)
    }
    @Test fun choosingAResultReplacesTheUnresolvedQueryButDifferentCitiesRemainSeparate() {
        val repo=NavigationPlacesRepository(MemorySettingsStore());repo.rememberSearch("Мира 1")
        repo.rememberSelection("Мира 1",home)
        val other=home.copy(point=GeoPoint(54.98,60.37),locality="Чебаркуль")
        repo.rememberSelection("Мира 1",other)
        assertEquals(2,repo.history.value.size)
        repo.rememberSelection("Мир 1 Челябинск",home)
        assertEquals(2,repo.history.value.size);assertEquals(home,repo.history.value.first().place)
    }
    @Test fun historyIsBoundedAndClearingItNeverDeletesFavorites() {
        val store=MemorySettingsStore();val repo=NavigationPlacesRepository(store)
        repo.saveFavorite(home,"Дом");repeat(40){repo.rememberSearch("Адрес $it")}
        assertEquals(30,repo.history.value.size);assertEquals("Адрес 39",repo.history.value.first().query)
        assertTrue(repo.clearHistory());assertTrue(repo.history.value.isEmpty())
        assertEquals("Дом",NavigationPlacesRepository(store).favorites.value.single().title)
    }
    @Test fun pointAliasesRenameOneFavoriteRatherThanCreatingDuplicates() {
        val repo=NavigationPlacesRepository(MemorySettingsStore())
        repo.saveFavorite(home,"Дом");repo.saveFavorite(home.copy(name="Другой ярлык"),"Квартира")
        assertEquals(1,repo.favorites.value.size);assertEquals("Квартира",repo.favorites.value.single().title)
        assertTrue(repo.removeFavorite(repo.favorites.value.single().id));assertTrue(repo.favorites.value.isEmpty())
    }
    @Test fun singleHistoryDeletionDoesNotChangeOtherEntriesOrFavorites() {
        val repo=NavigationPlacesRepository(MemorySettingsStore());repo.rememberSearch("Мира 1");repo.rememberSearch("Ленина 2");repo.saveFavorite(home,"Дом")
        repo.removeHistory(repo.history.value.last().id)
        assertEquals(listOf("Ленина 2"),repo.history.value.map {it.query});assertEquals(1,repo.favorites.value.size)
    }
    @Test fun favoriteLimitDoesNotSilentlyDropOldPointsAndExistingPointCanStillBeRenamed() {
        val repo=NavigationPlacesRepository(MemorySettingsStore())
        repeat(100){assertTrue(repo.saveFavorite(home.copy(point=GeoPoint(50.0+it*.001,61.0)),"Точка $it"))}
        val before=repo.favorites.value
        assertFalse(repo.saveFavorite(home,"Лишняя"));assertEquals(before,repo.favorites.value)
        assertTrue(repo.saveFavorite(before.last().place,"Новое имя"));assertEquals(100,repo.favorites.value.size)
    }
    @Test fun separatorsCyrillicAndEmojiRoundtripWithoutInjectingRows() {
        val store=MemorySettingsStore();val repo=NavigationPlacesRepository(store)
        val place=home.copy(name="Дом\t🏠\n1",address="Мира\t1\nкорпус 2")
        assertTrue(repo.saveFavorite(place,"Дом\t🏠\n1"))
        val restored=NavigationPlacesRepository(store)
        assertEquals(repo.favorites.value,restored.favorites.value)
        assertEquals(1,restored.favorites.value.size)
    }
    @Test fun failedDurableWriteDoesNotPublishPhantomHistoryOrFavorites() {
        val store=object:DurableSettingsStore {
            override fun string(key:String,default:String)=default
            override fun put(key:String,value:String)=Unit
            override fun putDurable(key:String,value:String){error("disk full")}
        }
        val repo=NavigationPlacesRepository(store)
        assertFalse(repo.saveFavorite(home,"Дом"));assertFalse(repo.rememberSelection("Мира 1",home))
        assertTrue(repo.history.value.isEmpty());assertTrue(repo.favorites.value.isEmpty());assertNotNull(repo.error.value)
    }
    @Test fun corruptStorageIsNotSilentlyOverwritten() {
        val store=MemorySettingsStore();store.put(NavigationPlacesRepository.FAVORITES,"corrupt")
        val repo=NavigationPlacesRepository(store)
        assertFalse(repo.saveFavorite(home,"Дом"));assertEquals("corrupt",store.string(NavigationPlacesRepository.FAVORITES,""))
        assertTrue(repo.clearHistory());assertEquals("corrupt",store.string(NavigationPlacesRepository.FAVORITES,""))
    }
    @Test fun invalidCoordinatesAndEmptyLabelsDoNotGetPersisted() {
        val repo=NavigationPlacesRepository(MemorySettingsStore())
        assertFalse(repo.saveFavorite(home.copy(point=GeoPoint(Double.NaN,61.0)),"Дом"))
        assertFalse(repo.rememberSelection("Мира 1",home.copy(point=GeoPoint(95.0,61.0))))
        assertFalse(repo.saveFavorite(home," "));assertTrue(repo.favorites.value.isEmpty());assertTrue(repo.history.value.isEmpty())
    }
}
