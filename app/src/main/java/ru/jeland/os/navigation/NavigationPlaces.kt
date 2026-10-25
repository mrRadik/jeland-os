package ru.jeland.os.navigation

import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.SettingsStore
import ru.jeland.os.core.DurableSettingsStore
import ru.jeland.os.core.validCoordinates
import kotlin.math.roundToLong

internal fun navigationPlaceKey(place:NavigationPlace):String {
    require(validCoordinates(place.point.latitude,place.point.longitude))
    return "${(place.point.latitude*1_000_000).roundToLong()}:${(place.point.longitude*1_000_000).roundToLong()}"
}
data class NavigationSearchHistory(val query:String,val place:NavigationPlace?,val at:Long) {
    val id get()=place?.let {"p:${navigationPlaceKey(it)}"}?:"q:${query.lowercase(java.util.Locale.ROOT)}"
}
data class NavigationFavorite(val title:String,val place:NavigationPlace) {
    val id get()=navigationPlaceKey(place)
    val destination get()=place.copy(name=title,address=place.address.ifBlank {place.name})
}

/** Versioned local-only format. Base64 fields cannot inject delimiters or additional records.
 * Separate keys let explicit history clearing recover bad history without touching favorites.
 */
internal object NavigationPlacesCodec {
    private fun text(value:String)=Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun read(value:String)=String(Base64.getDecoder().decode(value),Charsets.UTF_8).also {require(it.length<=1000)}
    private fun place(p:NavigationPlace):List<String> {
        navigationPlaceKey(p);require(p.name.isNotBlank()&&p.name.length<=500&&p.kind.length<=80&&p.locality.length<=500&&p.address.length<=500)
        return listOf(text(p.name),p.point.latitude.toString(),p.point.longitude.toString(),text(p.kind),text(p.locality),text(p.address))
    }
    private fun readPlace(fields:List<String>):NavigationPlace {
        require(fields.size==6)
        return NavigationPlace(read(fields[0]),GeoPoint(fields[1].toDouble(),fields[2].toDouble()),read(fields[3]),read(fields[4]),read(fields[5])).also {place(it)}
    }
    fun history(values:List<NavigationSearchHistory>):String = (listOf("JELAND_HISTORY_1")+values.map {entry->
        require(entry.query.isNotBlank()&&entry.query.length<=160&&entry.at>=0)
        (listOf(text(entry.query),entry.at.toString(),if(entry.place==null)"0"else"1")+(entry.place?.let(::place)?:emptyList())).joinToString("\t")
    }).joinToString("\n")
    fun favorites(values:List<NavigationFavorite>):String = (listOf("JELAND_FAVORITES_1")+values.map {entry->
        require(entry.title.isNotBlank()&&entry.title.length<=80)
        (listOf(text(entry.title))+place(entry.place)).joinToString("\t")
    }).joinToString("\n")
    private fun rows(value:String,header:String):List<String> {
        if(value.isEmpty())return emptyList()
        require(value.length<=1_000_000)
        val rows=value.lineSequence().toList();require(rows.first()==header)
        return rows.drop(1).filter {it.isNotEmpty()}
    }
    fun readHistory(value:String):List<NavigationSearchHistory> = rows(value,"JELAND_HISTORY_1").map {row->
        val fields=row.split('\t');require(fields.size in listOf(3,9))
        require((fields[2]=="0"&&fields.size==3)||(fields[2]=="1"&&fields.size==9))
        NavigationSearchHistory(read(fields[0]),if(fields[2]=="1")readPlace(fields.drop(3))else null,fields[1].toLong())
            .also {history(listOf(it))}
    }.distinctBy {it.id}.take(30)
    fun readFavorites(value:String):List<NavigationFavorite> = rows(value,"JELAND_FAVORITES_1").map {row->
        val fields=row.split('\t');require(fields.size==7)
        NavigationFavorite(read(fields[0]),readPlace(fields.drop(1))).also {favorites(listOf(it))}
    }.distinctBy {it.id}.take(100)
}

/** Small bounded collections, synchronously committed before publishing success. No network/GPS/route effects. */
class NavigationPlacesRepository(private val store:SettingsStore,private val clock:()->Long=System::currentTimeMillis) {
    private val problem=MutableStateFlow<String?>(null)
    val error=problem.asStateFlow()
    private var historyReadable=true
    private var favoritesReadable=true
    private val recent=MutableStateFlow(runCatching {NavigationPlacesCodec.readHistory(store.string(HISTORY,""))}.getOrElse {
        historyReadable=false;problem.value="Историю не удалось прочитать. Исходные данные сохранены.";emptyList()
    })
    private val saved=MutableStateFlow(runCatching {NavigationPlacesCodec.readFavorites(store.string(FAVORITES,""))}.getOrElse {
        favoritesReadable=false;problem.value="Избранное не удалось прочитать. Исходные данные сохранены.";emptyList()
    })
    val history=recent.asStateFlow()
    val favorites=saved.asStateFlow()
    private fun commit(key:String,value:String):Boolean = runCatching {
        if(store is DurableSettingsStore)store.putDurable(key,value)else store.put(key,value)
    }.fold({problem.value=null;true},{problem.value="Не удалось сохранить точки. Изменение не применено.";false})
    @Synchronized fun rememberSearch(query:String):Boolean=remember(query,null)
    @Synchronized fun rememberSelection(query:String,place:NavigationPlace):Boolean=remember(query.ifBlank {place.fullName},place)
    private fun remember(query:String,place:NavigationPlace?):Boolean {
        if(!historyReadable){problem.value="Сначала очистите повреждённую историю. Избранное не затрагивается.";return false}
        val clean=query.trim().replace(Regex("\\s+")," ").take(160)
        if(clean.length<2)return false
        val entry=NavigationSearchHistory(clean,place,clock().coerceAtLeast(0))
        val id=runCatching {entry.id}.getOrElse {problem.value="Некорректная точка";return false}
        val next=(listOf(entry)+recent.value.filterNot {it.id==id||(place!=null&&it.place==null&&it.query.equals(clean,true))}).take(30)
        val encoded=runCatching {NavigationPlacesCodec.history(next)}.getOrElse {problem.value="Некорректная точка";return false}
        if(!commit(HISTORY,encoded))return false
        recent.value=next;return true
    }
    @Synchronized fun removeHistory(id:String):Boolean {
        if(!historyReadable)return false
        val next=recent.value.filterNot {it.id==id}
        if(!commit(HISTORY,NavigationPlacesCodec.history(next)))return false
        recent.value=next;return true
    }
    @Synchronized fun clearHistory():Boolean {
        if(!commit(HISTORY,NavigationPlacesCodec.history(emptyList())))return false
        recent.value=emptyList();historyReadable=true;return true
    }
    @Synchronized fun saveFavorite(place:NavigationPlace,title:String):Boolean {
        if(!favoritesReadable){problem.value="Избранное не прочитано. Не перезаписываем исходные данные.";return false}
        val entry=NavigationFavorite(title.trim().take(80),place)
        val encoded=runCatching {
            require(entry.title.isNotBlank()){"Введите название точки"}
            require(saved.value.any {it.id==entry.id}||saved.value.size<100){"Можно сохранить до 100 избранных точек"}
            val next=listOf(entry)+saved.value.filterNot {it.id==entry.id}
            next to NavigationPlacesCodec.favorites(next)
        }.getOrElse {problem.value=it.message?:"Некорректная точка";return false}
        if(!commit(FAVORITES,encoded.second))return false
        saved.value=encoded.first;return true
    }
    @Synchronized fun removeFavorite(id:String):Boolean {
        if(!favoritesReadable)return false
        val next=saved.value.filterNot {it.id==id}
        if(!commit(FAVORITES,NavigationPlacesCodec.favorites(next)))return false
        saved.value=next;return true
    }
    companion object {
        internal const val HISTORY="navigation.search.history.v1"
        internal const val FAVORITES="navigation.places.favorites.v1"
    }
}
