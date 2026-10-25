package ru.jeland.os.navigation

import java.util.Locale

/** Partial address/locality search, with complete words and house numbers ranked first. */
internal fun searchNavigationPlaces(places:List<NavigationPlace>,query:String,limit:Int=100,checkCancelled:()->Unit={}):List<NavigationPlace> {
    val whitespace=Regex("\\s+")
    fun normalized(value:String)=value.lowercase(Locale.ROOT).replace('ё','е')
        .replace(',',' ').replace(';',' ').replace(whitespace," ").trim()
    val tokens=normalized(query).split(' ').filter {it.isNotBlank()}
    if(tokens.isEmpty()||limit<=0)return emptyList()
    // A house-number prefix matches 1/10/11/101, but not the middle of 21.
    val prefixes=tokens.map {Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}")}
    val matchers=tokens.mapIndexed {index,token->if(token.any {it.isDigit()})prefixes[index]else null}
    val complete=tokens.map {Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])")}
    var scanned=0
    return places.asSequence().mapNotNull {place->
        if(++scanned%512==0)checkCancelled()
        val name=normalized("${place.name} ${place.locality} ${place.address}")
        if(tokens.indices.all {index->matchers[index]?.containsMatchIn(name)?:name.contains(tokens[index])}) {
            val score=tokens.indices.sumOf {index->when {
                complete[index].containsMatchIn(name)->if(matchers[index]!=null)8 else 32
                matchers[index]==null&&prefixes[index].containsMatchIn(name)->16
                else->0
            }}
            place to score
        }else null
    }.sortedWith(compareByDescending<Pair<NavigationPlace,Int>> {it.second}
        .thenBy {if(it.first.kind=="address")0 else 1}
        .thenBy {it.first.name.length}.thenBy {it.first.name}.thenBy {it.first.locality}.thenBy {it.first.address})
        .take(limit).map {it.first}.toList()
}
