package ru.jeland.os.localization

import android.content.res.Configuration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

val LocalAppLanguage=staticCompositionLocalOf {"ru"}
private val LocalCatalog=staticCompositionLocalOf {TranslationCatalog(emptyMap())}
object AppLocale {@Volatile var current:Locale=Locale.forLanguageTag("ru");internal set}

/** App-scoped locale: does not change the vehicle, map data or media metadata. */
@Composable fun AppLocalization(language:String,content:@Composable ()->Unit) {
    AppLocale.current=Locale.forLanguageTag(language)
    val base=LocalContext.current
    val configuration=LocalConfiguration.current
    val catalog=remember(base.applicationContext) {
        val entries=base.assets.open("i18n/en.tsv").bufferedReader().useLines {lines->
            lines.filter {it.contains('\t')}.associate {line->
                val values=line.split('\t',limit=2)
                values[0].replace("\\n","\n") to values[1].replace("\\n","\n")
            }
        }
        TranslationCatalog(entries)
    }
    val localizedConfiguration=remember(configuration,language) {
        Configuration(configuration).apply {setLocale(Locale.forLanguageTag(language))}
    }
    // Preserve the Activity context: permission launchers and BackHandler discover their owners through it.
    CompositionLocalProvider(LocalAppLanguage provides language,LocalCatalog provides catalog,
        LocalConfiguration provides localizedConfiguration,content=content)
}

@Composable fun LocalizedIcon(imageVector:androidx.compose.ui.graphics.vector.ImageVector,contentDescription:String?,
    modifier:Modifier=Modifier,tint:Color=androidx.compose.material3.LocalContentColor.current) {
    androidx.compose.material3.Icon(imageVector,contentDescription?.let {localized(it)},modifier,tint)
}
@Composable fun LocalizedIcon(painter:androidx.compose.ui.graphics.painter.Painter,contentDescription:String?,
    modifier:Modifier=Modifier,tint:Color=androidx.compose.material3.LocalContentColor.current) {
    androidx.compose.material3.Icon(painter,contentDescription?.let {localized(it)},modifier,tint)
}

/** Exact phrases and explicit numbered templates. Never translates arbitrary place/track names. */
class TranslationCatalog(entries:Map<String,String>) {
    private val cache=object:LinkedHashMap<String,String>(128,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,String>?)=size>512
    }
    private val exact=entries.filterKeys {!it.contains(Regex("\\{[0-9]+\\}"))}
    private val folded=exact.mapKeys {it.key.lowercase(Locale.ROOT)}
    private val templates=entries.filterKeys {it.contains(Regex("\\{[0-9]+\\}"))}.map {(key,value)->
        val tokens=Regex("\\{([0-9]+)\\}").findAll(key).toList()
        var offset=0
        val pattern=buildString {append("^");tokens.forEach {append(Regex.escape(key.substring(offset,it.range.first)));append("(.+?)");offset=it.range.last+1};append(Regex.escape(key.substring(offset)));append("$")}
        Triple(Regex(pattern,RegexOption.DOT_MATCHES_ALL),tokens.map {it.groupValues[1].toInt()},value)
    }.sortedByDescending {it.first.pattern.length}
    @Synchronized fun translate(text:String):String {
        if(text.none {it in 'А'..'я'||it=='ё'||it=='Ё'})return text
        cache[text]?.let {return it}
        return resolve(text).also {cache[text]=it}
    }
    private fun resolve(text:String):String {
        exact[text]?.let {return it}
        folded[text.lowercase(Locale.ROOT)]?.let {return it}
        for((pattern,slots,translation)in templates) {
            val match=pattern.matchEntire(text)?:continue
            val arguments=slots.zip(match.groupValues.drop(1)).toMap()
            return Regex("\\{([0-9]+)\\}").replace(translation){arguments[it.groupValues[1].toInt()]?.let(::translate)?:it.value}
        }
        if(text.contains('\n'))return text.lines().joinToString("\n",transform=::translate)
        return text
    }
}

@Composable fun localized(text:String):String {
    if(LocalAppLanguage.current=="ru")return text
    return LocalCatalog.current.translate(text)
}

@Composable fun LocalizedText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
    fontSize:TextUnit=TextUnit.Unspecified,fontStyle:FontStyle?=null,fontWeight:FontWeight?=null,
    fontFamily:FontFamily?=null,letterSpacing:TextUnit=TextUnit.Unspecified,textDecoration:TextDecoration?=null,
    textAlign:TextAlign?=null,lineHeight:TextUnit=TextUnit.Unspecified,overflow:TextOverflow=TextOverflow.Clip,
    softWrap:Boolean=true,maxLines:Int=Int.MAX_VALUE,minLines:Int=1,onTextLayout:((TextLayoutResult)->Unit)?=null,
    style:TextStyle=LocalTextStyle.current) {
    androidx.compose.material3.Text(localized(text),modifier,color,fontSize,fontStyle,fontWeight,fontFamily,letterSpacing,
        textDecoration,textAlign,lineHeight,overflow,softWrap,maxLines,minLines,onTextLayout,style)
}

@Composable fun LocalizedText(text:AnnotatedString,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
    fontSize:TextUnit=TextUnit.Unspecified,fontWeight:FontWeight?=null,style:TextStyle=LocalTextStyle.current) {
    androidx.compose.material3.Text(text,modifier,color=color,fontSize=fontSize,fontWeight=fontWeight,style=style)
}
