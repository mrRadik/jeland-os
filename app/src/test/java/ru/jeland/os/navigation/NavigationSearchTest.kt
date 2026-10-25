package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationSearchTest {
    @Test fun organizationsShowTheirStreetAndHouseAsWellAsCity() {
        val store=NavigationPlace("Магнит",GeoPoint(55.0,60.0),"place","Челябинск","улица Мира 12")
        assertEquals("Челябинск · улица Мира 12",store.searchSubtitle)
        assertEquals("Магнит, улица Мира 12, Челябинск",store.fullName)
        for(query in listOf("Магнит Мира 12","Мира 12 Челябинск Магн","Челябинск, Магнит"))
            assertEquals(listOf(store),searchNavigationPlaces(listOf(store),query))
    }
    @Test fun sameBrandLocationsKeepDistinctAddressesAndNoCoordinatesInSubtitle() {
        val one=NavigationPlace("Магнит",GeoPoint(55.0,60.0),"place","Челябинск","улица Мира 1")
        val two=one.copy(address="улица Мира 10",point=GeoPoint(55.01,60.0))
        val results=searchNavigationPlaces(listOf(two,one),"Магнит Мира 1")
        assertEquals(listOf(one,two),results)
        assertEquals(2,results.map {it.searchSubtitle}.distinct().size)
        assertTrue(results.none {it.searchSubtitle.contains("55.")})
    }
    @Test fun addressQueryKeepsExactHouseAheadOfOrganizationsAtThatAddress() {
        val house=place("улица Мира 1","Чебаркуль")
        val store=NavigationPlace("Кафе",house.point,"place",house.locality,house.name)
        assertEquals(listOf(house,store),searchNavigationPlaces(listOf(store,house),"Мира 1"))
    }
    @Test fun missingOrganizationAddressIsNotInventedAndDuplicateComponentsAreRemoved() {
        val unknown=NavigationPlace("Кафе",GeoPoint(55.0,60.0),"place","Челябинск")
        assertEquals("Челябинск · Адрес не указан",unknown.searchSubtitle)
        assertEquals("Адрес не указан",unknown.copy(locality="").searchSubtitle)
        assertEquals("Мира 1, Челябинск",unknown.copy(name="Мира 1",kind="address",address="мира 1").fullName)
        assertEquals("Челябинск",unknown.copy(name="Мира 1",kind="address",address="мира 1").searchSubtitle)
    }
    private fun place(name:String,city:String)=NavigationPlace(name,GeoPoint(55.0,60.0),"address",city)
    private val places=listOf(place("улица Мира 1","Чебаркуль"),place("улица Мира 1","Сарафаново"),
        place("улица Мира 11","Чебаркуль"),place("улица Мира 101","Чебаркуль"),
        place("улица Мира 10","Чебаркуль"),place("улица Мира 12","Чебаркуль"),place("улица Мира 21","Чебаркуль"))
    @Test fun sameAddressInDifferentSettlementsRemainsDistinguishable() {
        val results=searchNavigationPlaces(places,"Мира 1")
        assertEquals(6,results.size)
        assertEquals(setOf("Чебаркуль","Сарафаново"),results.map {it.searchSubtitle}.toSet())
        assertEquals(6,results.map {it.fullName}.distinct().size)
    }
    @Test fun cityIsSearchableInAnyOrderAndCommaIsNotPartOfAddressToken() {
        for(query in listOf("Мира 1 Чебаркуль","Чебаркуль, Мира, 1","чебар МИРА 1")) {
            val results=searchNavigationPlaces(places,query)
            assertEquals(places[0],results.first())
            assertEquals(5,results.size)
            assertTrue(results.all {it.locality=="Чебаркуль"})
        }
    }
    @Test fun partialStreetAndHousePrefixMatchWithExactHouseFirst() {
        val results=searchNavigationPlaces(places,"Мира 1")
        assertEquals(results,searchNavigationPlaces(places,"Мир 1"))
        assertEquals(6,results.size)
        assertTrue(results.take(2).all {it.name=="улица Мира 1"})
        assertTrue(results.containsAll(listOf(places[2],places[3],places[4],places[5])))
        assertFalse(results.contains(places[6]))
        assertEquals(listOf(places[2]),searchNavigationPlaces(places,"Мира 11"))
        assertEquals(listOf(places[3]),searchNavigationPlaces(places,"Мира 101"))
    }
    @Test fun missingCityIsExplicitRatherThanGuessedFromCoordinates() {
        val legacy=NavigationPlace("улица Мира 1",GeoPoint(55.0,60.0),"address")
        assertEquals("Населённый пункт не указан",legacy.searchSubtitle)
        assertEquals(legacy.name,legacy.fullName)
        assertEquals(listOf(legacy),searchNavigationPlaces(listOf(legacy),"Мира 1"))
        assertTrue(searchNavigationPlaces(listOf(legacy),"Челябинск Мира 1").isEmpty())
    }
    @Test fun settlementDoesNotRepeatItsOwnNameAndYoMatchesYe() {
        val settlement=NavigationPlace("Озёрск",GeoPoint(55.0,60.0),"settlement","Озёрск")
        assertEquals("Озёрск",settlement.fullName)
        assertEquals("Населённый пункт",settlement.searchSubtitle)
        assertEquals(listOf(settlement),searchNavigationPlaces(listOf(settlement),"озерск"))
    }
    @Test fun searchLimitsAndCoordinateInputAreNotAddressResults() {
        assertTrue(searchNavigationPlaces(places,"").isEmpty())
        assertTrue(searchNavigationPlaces(places,"55.0,60.0").isEmpty())
        assertEquals(1,searchNavigationPlaces(places,"Мира",1).size)
    }
    @Test fun streetBeginningRanksAboveAnIncidentalSubstringInLocality() {
        val incidental=place("1","Тимирязевский")
        val results=searchNavigationPlaces(listOf(incidental,places[4],places[0]),"Мир 1")
        assertEquals(listOf(places[0],places[4],incidental),results)
    }
}
