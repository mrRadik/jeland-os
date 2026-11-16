package ru.jeland.os.ui.cluster

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.*
import ru.jeland.os.ui.AlbumArtwork
import ru.jeland.os.ui.MinimalMusicProgress
import ru.jeland.os.ui.MusicMarqueeText
import ru.jeland.os.ui.musicDisplayTitle
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.LocalJelandColors

/** Same player/session as the dashboard. Bounds use the cluster's 2048 x 768 coordinate space. */
internal data class ClusterMusicBounds(val left:Float,val top:Float,val width:Float,val height:Float)
internal fun clusterMusicBounds(design:ClusterDesign):ClusterMusicBounds?=when(design) {
    ClusterDesign.CLASSIC->ClusterMusicBounds(752f,508f,544f,144f)
    ClusterDesign.SPORT->ClusterMusicBounds(1392f,456f,466f,154f)
    ClusterDesign.NAVIGATION->ClusterMusicBounds(1384f,108f,576f,144f)
    ClusterDesign.ORIGINAL->null
}

@Composable internal fun BoxWithConstraintsScope.ClusterMusicOverlay(
    media:MediaState,design:ClusterDesign,dark:Boolean,
    accent:ClusterAccent,miniature:Boolean=false,
) {
    val bounds=clusterMusicBounds(design)?:return
    val density=LocalDensity.current
    // Read-only metadata stays proportional to the gauges, including small radio previews.
    val scale=maxWidth.value/2048f
    val innerScale=minOf(1f,bounds.height/144f)
    val colors=Jeland.colors.copy(
        text=if(dark)Color(0xFFF5FAFF)else Color(0xFF0D1D2A),
        muted=if(dark)Color(0xFF9DCEE9)else Color(0xFF36546A),
        accent=Color(if(dark)accent.darkArgb else accent.lightArgb),
    )
    Box(Modifier.offset(x=maxWidth*(bounds.left/2048f),y=maxHeight*(bounds.top/768f))
        .width(maxWidth*(bounds.width/2048f)).height(maxHeight*(bounds.height/768f))
        .clipToBounds().testTag(if(miniature)"cluster-music-thumbnail-${design.name.lowercase()}"else"cluster-music")) {
        CompositionLocalProvider(LocalDensity provides Density(density.density*scale*innerScale,fontScale=1f),LocalJelandColors provides colors) {
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().weight(1f),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    AlbumArtwork(media,Modifier.size(102.dp),square=true)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        MusicMarqueeText(musicDisplayTitle(media),colors.text,30.sp,Modifier.testTag("cluster-music-title"))
                        if(media.track.artist.isNotBlank())MusicMarqueeText(media.track.artist,colors.muted,24.sp,Modifier.testTag("cluster-music-artist"))
                    }
                }
                MinimalMusicProgress(media,Modifier.fillMaxWidth())
            }
        }
    }
}
