package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedText as Text

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.jeland.os.R
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import kotlin.math.*

/** One-line viewport: only overflowing metadata moves; a new track starts at its beginning. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun MusicMarqueeText(text:String,color:Color,fontSize:TextUnit,modifier:Modifier=Modifier) {
    key(text) {
        androidx.compose.material3.Text(text.replace('\n',' ').replace('\r',' '),color=color,fontSize=fontSize,maxLines=1,softWrap=false,
            overflow=TextOverflow.Clip,modifier=modifier.fillMaxWidth().clipToBounds()
                .basicMarquee(iterations=Int.MAX_VALUE,repeatDelayMillis=1800,velocity=28.dp))
    }
}
@Composable internal fun musicDisplayTitle(state:MediaState):String =
    if(state.live&&state.liveTrack==null)ru.jeland.os.localization.localized("Музыка")else state.track.title

@Composable fun AlbumArtwork(s:MediaState,modifier:Modifier=Modifier,square:Boolean=false) {
    val art=s.artwork
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,art?.key) {
        value=withContext(Dispatchers.Default){art?.bytes?.let {runCatching {BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap()}.getOrNull()}}
    }
    val shape=if(square)androidx.compose.foundation.shape.RoundedCornerShape(10.dp)else Jeland.card
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(Jeland.colors.deep,Jeland.colors.panel,Jeland.colors.deep)))
        .then(if(square)Modifier.border(1.dp,Jeland.colors.accent.copy(alpha=.4f),shape)else Modifier),contentAlignment=Alignment.Center) {
        when {
            bitmap!=null->Image(bitmap!!,"Обложка альбома",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            !s.live->Image(painterResource(R.drawable.hero_jeland),"Обложка демонстрационной композиции",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else->{
                Canvas(Modifier.fillMaxSize()) {
                    val radius=size.minDimension*.37f
                    repeat(5){i->drawCircle(androidx.compose.ui.graphics.Color(0xff1587b0).copy(alpha=.14f),radius*(1f-i*.15f),style=androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension*.015f))}
                }
                Glyph(JIcons.MusicNote,"Нет обложки",Modifier.fillMaxSize(.28f),true)
            }
        }
    }
}

/** Decorative playback motion, not an audio spectrum. No microphone, capture or audio focus. */
@Composable private fun PlaybackWaves(playing:Boolean,modifier:Modifier=Modifier) {
    val phase=if(playing) {
        val transition=rememberInfiniteTransition(label="Playback")
        transition.animateFloat(0f,6.283185f,infiniteRepeatable(tween(2400,easing=LinearEasing)),label="Waves").value
    }else 0f
    val accent=Jeland.colors.accent;val muted=Jeland.colors.outline
    Canvas(modifier.testTag(if(playing)"music-waves-playing"else"music-waves-paused")) {
        val count=48;val cell=size.width/count
        repeat(count){i->
            val envelope=sin((i+1f)/(count+1)*PI).toFloat()
            val amplitude=if(playing)(.22f+.78f*abs(sin(i*.61f+phase))*abs(cos(i*.13f-phase*.4f)))else .08f
            val h=(size.height*envelope*amplitude).coerceAtLeast(3.dp.toPx())
            drawRoundRect(if(playing)accent.copy(alpha=.35f+envelope*.65f)else muted,Offset(i*cell+cell*.2f,(size.height-h)/2),Size(cell*.6f,h),CornerRadius(cell/2,cell/2))
        }
    }
}

@Composable fun FullMusicPlayer(s:MediaState,repository:MediaRepository) {
    val r=repository;val c=Jeland.colors
    var showPlaylist by remember {mutableStateOf(false)}
    val hasQueue=!s.live||s.source.startsWith("USB",ignoreCase=true)
    val queue=if(s.live)s.playlist else demoTracks.mapIndexed {i,t->MediaQueueItem(i.toString(),t)}
    LaunchedEffect(hasQueue){if(!hasQueue)showPlaylist=false}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val playerHeight=maxHeight
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(playerHeight).clip(Jeland.card).background(c.background).border(1.dp,c.outline,Jeland.card).testTag("full-music-player")) {
            AlbumArtwork(s,Modifier.fillMaxSize().blur(32.dp).alpha(.32f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.background.copy(alpha=.2f),c.background.copy(alpha=.65f),c.background))))
            Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cover=minOf(maxWidth*.56f,maxHeight-150.dp,440.dp).coerceAtLeast(80.dp)
                    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Box(Modifier.fillMaxWidth().height(cover+32.dp),contentAlignment=Alignment.Center) {
                        PlaybackWaves(s.playing,Modifier.fillMaxWidth().height(cover*.7f))
                        Canvas(Modifier.size(cover*1.5f)) {
                            repeat(4){i->drawCircle(c.accent.copy(alpha=.04f+i*.015f),size.minDimension*(.34f+i*.045f),style=Stroke(1.dp.toPx()))}
                        }
                        AlbumArtwork(s,Modifier.size(cover).border(1.5.dp,c.accent.copy(alpha=.8f),Jeland.card).testTag("music-main-artwork"))
                    }
                    Text(s.track.title,color=c.text,fontSize=36.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(top=16.dp).testTag("media-title"))
                    if(s.track.artist.isNotBlank())Text(s.track.artist,color=c.muted,fontSize=23.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
                    }
                }
                MusicProgress(s,r,Modifier.fillMaxWidth().padding(top=20.dp))
                MusicTransport(s,r,Modifier.fillMaxWidth().padding(top=18.dp,bottom=28.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Panel(Modifier.weight(1f).height(84.dp)) {
                        Row(Modifier.fillMaxSize().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Glyph(if(s.source.contains("Bluetooth",true))JIcons.Bluetooth else JIcons.MusicNote,null,Modifier.size(36.dp),true)
                            Column(Modifier.weight(1f)) {
                                Text(s.source,color=c.text,fontSize=19.sp,maxLines=1,modifier=Modifier.testTag("music-active-source"))
                                Text(if(hasQueue)"Плейлист · ${queue.size} композиций  ›"else"Аудио автомобиля",color=if(hasQueue)c.accent else c.muted,fontSize=13.sp,modifier=Modifier
                                    .then(if(hasQueue)Modifier.testTag("music-open-playlist").clickable(role=Role.Button){showPlaylist=true}else Modifier).padding(top=4.dp,bottom=6.dp))
                            }
                        }
                    }
                    if(!s.live)Panel(Modifier.weight(1f).height(84.dp)) {
                        Row(Modifier.fillMaxSize().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("♪",color=c.accent,fontSize=28.sp)
                            JelandSlider(s.volume.toFloat(),{r.setVolume(it.toInt())},valueRange=0f..100f,modifier=Modifier.weight(1f).testTag("media-volume"))
                            Text("${s.volume}",color=c.text,fontSize=18.sp)
                        }
                    }
                }
            }
        }
        if(!s.live)Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {listOf("Bluetooth","USB","Радио").forEach {source->Control(source,Modifier.weight(1f).testTag("media-source-$source"),selected=s.source==source){r.setSource(source)}}}
            }
        }
    }
    }
    if(showPlaylist&&hasQueue)Dialog(onDismissRequest={showPlaylist=false}) {
        Panel(Modifier.fillMaxWidth().testTag("music-playlist-panel")) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Label(if(s.live)"Плейлист USB"else"Коллекция");Label("${queue.size} композиций",small=true,muted=true)}
                    RoundControl(JIcons.Close,"Закрыть плейлист",Modifier.testTag("music-close-playlist")){showPlaylist=false}
                }
                if(queue.isEmpty())Label("Список композиций пока недоступен",small=true,muted=true)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max=450.dp).testTag("music-playlist")) {
                    itemsIndexed(queue,key={_,item->item.id}){index,item->
                        val selected=if(s.live)item.id==s.currentId else index==s.index
                        Row(Modifier.fillMaxWidth().clip(Jeland.control).background(if(selected)c.accent.copy(alpha=.1f)else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled=!s.live||s.controlsEnabled){r.selectTrack(index)}.testTag("media-track-$index").padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            if(selected)Glyph(if(s.playing)JIcons.Pause else JIcons.PlayArrow,null,Modifier.size(24.dp),true)else Label("${index+1}",small=true,muted=true,modifier=Modifier.width(24.dp))
                            Column(Modifier.weight(1f)){Text(item.track.title,color=if(selected)c.accent else c.text,fontSize=17.sp,maxLines=1,overflow=TextOverflow.Ellipsis);if(item.track.artist.isNotBlank())Label(item.track.artist,small=true,muted=true)}
                            if(item.track.duration>0)Label(timeString(item.track.duration),small=true,muted=true)
                        }
                    }
                }
            }
        }
}

}

@Composable fun MusicProgress(s:MediaState,r:MediaRepository,modifier:Modifier=Modifier) {
    Column(modifier) {
        JelandSlider(s.position.toFloat(),{r.seek(it.toInt())},valueRange=0f..s.track.duration.coerceAtLeast(1).toFloat(),enabled=!s.live,modifier=Modifier.height(32.dp).testTag("media-seek"))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(if(s.live&&!s.positionKnown)"—:—"else timeString(s.position),small=true,muted=true)
            Label(if(s.live&&s.track.duration<=0)"—:—"else timeString(s.track.duration),small=true,muted=true)
        }
    }
}

/** Read-only timeline: phone transports report playback position but do not support seeking. */
@Composable internal fun MinimalMusicProgress(s:MediaState,modifier:Modifier=Modifier) {
    val duration=s.track.duration
    val known=duration>0&&(!s.live||s.positionKnown)
    val progress=if(known)s.position.coerceIn(0,duration).toFloat()/duration else 0f
    val colors=Jeland.colors
    val unavailable=ru.jeland.os.localization.localized("Прогресс воспроизведения недоступен")
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(4.dp).testTag("minimal-music-progress")
            .then(if(known)Modifier.progressSemantics(progress)else Modifier.semantics {contentDescription=unavailable})) {
            val radius=CornerRadius(size.height/2)
            drawRoundRect(colors.outline.copy(alpha=.6f),cornerRadius=radius)
            if(progress>0f)drawRoundRect(colors.accent,size=Size(size.width*progress,size.height),cornerRadius=radius)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(s.live&&!s.positionKnown)"—:—"else timeString(s.position.coerceAtLeast(0)),color=colors.muted,fontSize=Jeland.type.micro,
                modifier=Modifier.testTag("minimal-music-elapsed"))
            Text(if(duration>0)timeString(duration)else"—:—",color=colors.muted,fontSize=Jeland.type.micro,
                modifier=Modifier.testTag("minimal-music-duration"))
        }
    }
}

/** Filled transport glyphs, independent of the thin outline icons used elsewhere. */
@Composable fun MusicTransport(s:MediaState,r:MediaRepository,modifier:Modifier=Modifier,compact:Boolean=false,borderless:Boolean=false) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(if(borderless)8.dp else if(compact)28.dp else 64.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
        MusicTransportButton(-1,false,"Предыдущая композиция",Modifier.size(if(borderless)54.dp else if(compact)58.dp else 108.dp).testTag("media-previous"),s.controlsEnabled,false,borderless,r::previous)
        MusicTransportButton(0,s.playing,if(s.playing)"Пауза"else"Воспроизвести",Modifier.size(if(borderless)54.dp else if(compact)72.dp else 136.dp).testTag("media-play"),s.controlsEnabled,true,borderless,r::togglePlay)
        MusicTransportButton(1,false,"Следующая композиция",Modifier.size(if(borderless)54.dp else if(compact)58.dp else 108.dp).testTag("media-next"),s.controlsEnabled,false,borderless,r::next)
    }
}

@Composable private fun MusicTransportButton(direction:Int,pausedGlyph:Boolean,description:String,modifier:Modifier,enabled:Boolean,primary:Boolean,borderless:Boolean,onClick:()->Unit) {
    val c=Jeland.colors
    val surface=if(borderless)Modifier else Modifier.background(Brush.radialGradient(listOf(if(primary)c.deep.copy(alpha=.65f)else c.raised.copy(alpha=.4f),c.background.copy(alpha=.55f))))
        .border(if(primary)2.dp else 1.dp,if(primary)c.accent else c.outline,CircleShape)
    Box(modifier.alpha(if(enabled)1f else .38f).clip(CircleShape).then(surface)
        .semantics {contentDescription=description}.clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize(if(borderless).48f else .36f)) {
            val tint=c.text
            if(direction==0&&pausedGlyph) {
                drawRoundRect(tint,Offset(size.width*.12f,0f),Size(size.width*.27f,size.height),CornerRadius(size.width*.06f))
                drawRoundRect(tint,Offset(size.width*.61f,0f),Size(size.width*.27f,size.height),CornerRadius(size.width*.06f))
            }else {
                val previous=direction<0
                val path=Path().apply {
                    if(previous){moveTo(size.width*.82f,size.height*.08f);lineTo(size.width*.18f,size.height*.5f);lineTo(size.width*.82f,size.height*.92f)}
                    else{moveTo(size.width*.18f,size.height*.08f);lineTo(size.width*.82f,size.height*.5f);lineTo(size.width*.18f,size.height*.92f)}
                    close()
                }
                drawPath(path,tint)
                if(direction!=0)drawRoundRect(tint,Offset(size.width*(if(previous).04f else .84f),size.height*.08f),Size(size.width*.12f,size.height*.84f),CornerRadius(size.width*.04f))
            }
        }
    }
}
