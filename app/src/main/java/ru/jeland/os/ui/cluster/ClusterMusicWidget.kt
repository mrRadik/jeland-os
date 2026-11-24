package ru.jeland.os.ui.cluster

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.*
import ru.jeland.os.ui.MusicMarqueeText
import ru.jeland.os.ui.musicDisplayTitle
import ru.jeland.os.ui.timeString
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.LocalJelandColors
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.localization.localized
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign

/** Same player/session as the dashboard. Bounds use the cluster's 2048 x 768 coordinate space. */
internal data class ClusterMusicBounds(val left:Float,val top:Float,val width:Float,val height:Float)
internal fun clusterMusicBounds(design:ClusterDesign):ClusterMusicBounds?=when(design) {
    ClusterDesign.CLASSIC->ClusterMusicBounds(752f,616f,544f,56f)
    ClusterDesign.SPORT->ClusterMusicBounds(220f,542f,456f,56f)
    ClusterDesign.NAVIGATION->ClusterMusicBounds(656f,606f,736f,56f)
    ClusterDesign.ORIGINAL->null
}

/** Permanent vehicle warnings take priority over media when their reserved rack is occupied. */
internal fun clusterMusicCovered(panel:ClusterPanelState,design:ClusterDesign):Boolean {
    val b=clusterMusicBounds(design)?:return false
    val native=panel.indicators?:ClusterJ7Indicators.fallback(panel.lamps)
    return ClusterIndicatorLayout.place(ClusterJ7Indicators.presentation(native),false,design).any {
        !it.header&&it.x<b.left+b.width&&it.x+it.size>b.left&&it.y<b.top+b.height&&it.y+it.size>b.top
    }
}

@Composable internal fun BoxWithConstraintsScope.ClusterMusicOverlay(
    media:MediaState,design:ClusterDesign,dark:Boolean,
    accent:ClusterAccent,miniature:Boolean=false,
) {
    val bounds=clusterMusicBounds(design)?:return
    val density=LocalDensity.current
    // Read-only metadata stays proportional to the gauges, including small radio previews.
    val scale=maxWidth.value/2048f
    val colors=Jeland.colors.copy(
        text=if(dark)Color(0xFFF5FAFF)else Color(0xFF0D1D2A),
        muted=if(dark)Color(0xFF9DCEE9)else Color(0xFF36546A),
        accent=Color(if(dark)accent.darkArgb else accent.lightArgb),
    )
    Box(Modifier.offset(x=maxWidth*(bounds.left/2048f),y=maxHeight*(bounds.top/768f))
        .width(maxWidth*(bounds.width/2048f)).height(maxHeight*(bounds.height/768f))
        .testTag(if(miniature)"cluster-music-thumbnail-${design.name.lowercase()}"else"cluster-music")) {
        CompositionLocalProvider(LocalDensity provides Density(density.density*scale,fontScale=1f),LocalJelandColors provides colors) {
            val duration=media.track.duration
            val elapsedKnown=!media.live||media.positionKnown
            val progressKnown=elapsedKnown&&duration>0
            val position=media.position.coerceAtLeast(0).let {if(duration>0)it.coerceAtMost(duration)else it}
            val progress=if(progressKnown)position.toFloat()/duration else 0f
            val elapsed=if(elapsedKnown)timeString(position)else"—:—"
            val remaining=if(progressKnown)"−"+timeString(duration-position)else"—:—"
            val metadata=listOf(media.track.artist,musicDisplayTitle(media)).filter(String::isNotBlank).joinToString(" — ")
            val unavailable=localized("Прогресс воспроизведения недоступен")
            val shape=RoundedCornerShape(9.dp)
            Box(Modifier.fillMaxSize().clip(shape)
                .background(if(dark)Color(0xFF07131E)else Color(0xFFEDF5FA))
                .border(1.dp,colors.accent.copy(alpha=.4f),shape)) {
                Canvas(Modifier.matchParentSize().testTag("minimal-music-progress")
                    .then(if(progressKnown)Modifier.progressSemantics(progress)else Modifier.semantics {contentDescription=unavailable})) {
                    if(progress>0f)drawRect(Brush.horizontalGradient(listOf(colors.accent.copy(alpha=.18f),colors.accent.copy(alpha=.35f))),
                        size=Size(size.width*progress,size.height))
                }
                Row(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(elapsed,Modifier.width(maxOf(80,elapsed.length*14).dp).testTag("cluster-music-elapsed"),color=colors.text,fontSize=22.sp,
                        fontFamily=FontFamily.Monospace,maxLines=1,softWrap=false)
                    MusicMarqueeText(metadata,colors.text,24.sp,Modifier.weight(1f).testTag("cluster-music-title"))
                    Text(remaining,Modifier.width(maxOf(88,remaining.length*14).dp).testTag("cluster-music-remaining"),color=colors.text,fontSize=22.sp,
                        fontFamily=FontFamily.Monospace,textAlign=TextAlign.End,maxLines=1,softWrap=false)
                }
            }
        }
    }
}
