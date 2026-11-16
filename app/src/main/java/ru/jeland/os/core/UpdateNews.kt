package ru.jeland.os.core

/** A receipt is armed only when the built-in updater starts installing a validated APK. */
data class UpdateNews(val versionCode:Long,val versionName:String,val body:String="")
class UpdateNewsTracker(private val store:SettingsStore) {
    private fun put(key:String,value:String) {if(store is DurableSettingsStore)store.putDurable(key,value)else store.put(key,value)}
    private fun seen()=store.string(SEEN,"0").toLongOrNull()?:0L
    private fun encode(from:Long,news:UpdateNews)=listOf(from.toString(),news.versionCode.toString(),news.versionName.replace('\n',' ').replace('\r',' '),news.body.replace("\u0000","").take(12000)).joinToString("\n")
    private fun read(key:String):Pair<Long,UpdateNews>?=runCatching {
        val fields=store.string(key,"").split('\n',limit=4)
        require(fields.size==4)
        val from=fields[0].toLong();val code=fields[1].toLong()
        require(from>=0&&code>from&&fields[2].isNotBlank()&&fields[2].length<=100&&fields[3].length<=12000)
        from to UpdateNews(code,fields[2],fields[3])
    }.getOrNull()
    @Synchronized fun arm(installed:Long,news:UpdateNews) {
        require(installed>=0&&news.versionCode>installed&&news.versionName.isNotBlank()&&news.versionName.length<=100)
        put(ATTEMPT,encode(installed,news))
    }
    @Synchronized fun cancel(){put(ATTEMPT,"")}
    @Synchronized fun captureInstalled(actual:Long) {
        val receipt=read(ATTEMPT)
        if(receipt!=null&&actual>=receipt.second.versionCode) {
            if(actual==receipt.second.versionCode&&seen()<actual)put(READY,encode(receipt.first,receipt.second))
            put(ATTEMPT,"")
        }
        if(read(READY)?.second?.versionCode?.let {it!=actual||it<=seen()}==true)put(READY,"")
    }
    @Synchronized fun pending(actual:Long)=read(READY)?.second?.takeIf {it.versionCode==actual&&it.versionCode>seen()}
    @Synchronized fun shown(version:Long) {
        if(read(READY)?.second?.versionCode!=version)return
        put(SEEN,maxOf(seen(),version).toString());put(READY,"")
    }
    companion object {
        const val ATTEMPT="updates.news.attempt"
        const val READY="updates.news.ready"
        const val SEEN="updates.news.seen"
        const val SUPPORTED="updates.news.supported"
    }
}

/** Release Markdown is displayed as bounded plain text, with no HTML or URL execution. */
fun plainReleaseNotes(body:String):String=body.replace("\r","").replace("\u0000","").take(12000)
    .lineSequence().filterNot {it.trimStart().startsWith("```")}.map {line->
        line.replace(Regex("^\\s{0,3}#{1,6}\\s+"),"")
            .replace(Regex("^\\s*[-*]\\s+"),"• ")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"),"$1")
            .replace("**","").replace("__","").replace("`","")
    }.joinToString("\n").trim()
